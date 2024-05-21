package com.commercetools.queue.testkit

import cats.effect.std.Random
import cats.effect.{IO, Ref, Resource}
import com.commercetools.queue.QueueClient
import fs2.{Chunk, Stream}
import munit.CatsEffectSuite

import scala.concurrent.duration._

/**
 * This suite tests that the basic features of a [[com.commercetools.queue.QueueClient QueueClient]] are properly
 * implemented for a concrete client.
 * This is used in integration tests for the various implemented queue providers.
 */
abstract class QueueClientSuite extends CatsEffectSuite {

  /** Provide a way to acquire a queue client for the provider under test. */
  def client: Resource[IO, QueueClient[IO]]

  val clientFixture = ResourceSuiteLocalFixture("queue-client", client)

  override def munitFixtures = List(clientFixture)

  val withQueue =
    ResourceFixture(
      Resource.make(
        IO.randomUUID
          .map(uuid => s"queue-$uuid")
          .flatTap { queueName =>
            clientFixture().administration
              .create(queueName, 10.minutes, 2.minutes)
          })(queueName => clientFixture().administration.delete(queueName)))

  withQueue.test("published messages are received by a processor") { queueName =>
    for {
      random <- Random.scalaUtilRandom[IO]
      size <- random.nextLongBounded(30L)
      messages = List.range(0L, size).map(_.toString())
      received <- Ref[IO].of(List.empty[String])
      client = clientFixture()
      _ <- Stream
        .emits(messages)
        .through(client.publish(queueName).sink(batchSize = 10))
        .merge(
          client
            .subscribe(queueName)
            .processWithAutoAck(batchSize = 10, waitingTime = 20.seconds)(msg => received.update(msg.rawPayload :: _))
            .take(size)
        )
        .compile
        .drain
      _ <- assertIO(received.get.map(_.toSet), messages.toSet)
    } yield ()
  }

  withQueue.test("puller returns no messages if none is available during the configured duration") { queueName =>
    val client = clientFixture()
    client.subscribe(queueName).puller.use { puller =>
      assertIO(puller.pullBatch(10, 2.seconds), Chunk.empty)
    }
  }

  withQueue.test("existing queue should be indicated as such") { queueName =>
    val client = clientFixture()
    assertIO(client.administration.exists(queueName), true)
  }

  test("non existing queue should be indicated as such") {
    val client = clientFixture()
    assertIO(client.administration.exists("not-existing"), false)
  }

  withQueue.test("delayed messages should not be pulled before deadline") { queueName =>
    val client = clientFixture()
    client.publish(queueName).pusher.use { pusher =>
      pusher.push("delayed message", Some(2.seconds))
    } *> client.subscribe(queueName).puller.use { puller =>
      for {
        _ <- assertIO(puller.pullBatch(1, 1.second), Chunk.empty)
        _ <- IO.sleep(2.seconds)
        _ <- assertIO(puller.pullBatch(1, 1.second).map(_.map(_.rawPayload)), Chunk("delayed message"))
      } yield ()

    }
  }

}