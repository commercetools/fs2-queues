package com.commercetools.queue.testkit

import cats.effect.std.Random
import cats.effect.{IO, Ref, Resource}
import com.commercetools.queue.{QueueClient, QueueConfiguration}
import fs2.{Chunk, Stream}
import munit.CatsEffectSuite

import scala.concurrent.duration._

/**
 * This suite tests that the basic features of a [[com.commercetools.queue.QueueClient QueueClient]] are properly
 * implemented for a concrete client.
 * This is used in integration tests for the various implemented queue providers.
 */
abstract class QueueClientSuite extends CatsEffectSuite {

  val queueUpdateSupported: Boolean = true

  /** Provide a way to acquire a queue client for the provider under test. */
  def client: Resource[IO, QueueClient[IO]]

  val clientFixture = ResourceSuiteLocalFixture("queue-client", client)

  val originalMessageTTL = 10.minutes
  val originalLockTTL = 2.minutes

  override def munitFixtures = List(clientFixture)

  val withQueue =
    ResourceFixture(
      Resource.make(
        IO.randomUUID
          .map(uuid => s"queue-$uuid")
          .flatTap { queueName =>
            clientFixture().administration
              .create(queueName, originalMessageTTL, originalLockTTL)
          })(queueName => clientFixture().administration.delete(queueName)))

  withQueue.test("published messages are received by a processor") { queueName =>
    for {
      random <- Random.scalaUtilRandom[IO]
      size <- random.nextLongBounded(30L)
      messages = List
        .range(0L, size)
        .map(i => (i.toString, Map(s"metadata-$i-key" -> s"$i-value", s"metadata-$i-another-key" -> "another-value")))
      received <- Ref[IO].of(List.empty[(String, Map[String, String])])
      client = clientFixture()
      _ <- Stream
        .emits(messages)
        .through(client.publish(queueName).sink(batchSize = 10))
        .merge(
          client
            .subscribe(queueName)
            .processWithAutoAck(batchSize = 10, waitingTime = 20.seconds)(msg =>
              received.update((msg.rawPayload, msg.metadata) :: _))
            .take(size)
        )
        .compile
        .drain
      _ <- assertIO(
        received.get.map(_.map(x => (x._1, keepMetadataWithPrefix(x._2, "metadata"))).toSet),
        messages.toSet)
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
      pusher.push("delayed message", Map("metadata-key" -> "value"), Some(2.seconds))
    } *> client.subscribe(queueName).puller.use { puller =>
      for {
        _ <- assertIO(puller.pullBatch(1, 1.second), Chunk.empty)
        _ <- IO.sleep(2.seconds)
        _ <- assertIO(
          puller
            .pullBatch(1, 1.second)
            .map(_.map(x => (x.rawPayload, keepMetadataWithPrefix(x.metadata, "metadata")))),
          Chunk(("delayed message", Map("metadata-key" -> "value")))
        )
      } yield ()

    }
  }

  withQueue.test("configuration can be updated") { queueName =>
    assume(queueUpdateSupported, "The test environment does not support queue update")
    val client = clientFixture()
    val admin = client.administration
    for {
      _ <- assertIO(admin.configuration(queueName), QueueConfiguration(originalMessageTTL, originalLockTTL))
      _ <- admin.update(queueName, Some(originalMessageTTL + 1.minute), Some(originalLockTTL + 10.seconds))
      _ <- assertIO(
        admin.configuration(queueName),
        QueueConfiguration(originalMessageTTL + 1.minute, originalLockTTL + 10.seconds))
    } yield ()
  }

  // to only keep metadata entries we are interested in, in not the ones set by the provider
  private def keepMetadataWithPrefix(metadata: Map[String, String], prefix: String): Map[String, String] =
    metadata.filter(_._1.startsWith(prefix))

}
