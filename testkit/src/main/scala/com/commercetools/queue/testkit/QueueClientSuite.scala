package com.commercetools.queue.testkit

import cats.effect.std.Random
import cats.effect.{IO, Ref, Resource}
import com.commercetools.queue.QueueClient
import fs2.Stream
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
            .processWithAutoAck(batchSize = 10, waitingTime = 20.seconds)(msg => received.update(msg.payload :: _))
            .take(size)
        )
        .compile
        .drain
      _ <- assertIO(received.get.map(_.toSet), messages.toSet)
    } yield ()
  }

}
