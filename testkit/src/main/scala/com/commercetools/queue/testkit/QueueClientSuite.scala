package com.commercetools.queue.testkit

import cats.effect.std.Random
import cats.effect.{IO, Ref, Resource}
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxOptionId}
import com.commercetools.queue.{Decision, Message, QueueClient, QueueConfiguration}
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
              received.update(_ :+ (msg.rawPayload, msg.metadata)))
            .take(size)
        )
        .compile
        .drain
      _ <- assertIO_(received.get.map { receivedMessages =>
        if (receivedMessages.size != messages.size)
          fail(s"expected to receive ${messages.size} messages, got ${receivedMessages.size}")

        messages.zip(receivedMessages).forall {
          case ((expectedPayload, expectedMetadata), (actualPayload, actualMetadata)) =>
            if (expectedPayload != actualPayload)
              fail(s"expected payload '$expectedPayload', got '$actualPayload'")
            else if (!metadataContains(actualMetadata, expectedMetadata))
              fail(s"expected metadata to contain '$expectedMetadata', got '$actualMetadata'")
            else true
        }
      }.void)
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
        msg <- puller
          .pullBatch(1, 1.second)
          .map(_.head.getOrElse(fail("expected a message, got nothing.")))
        _ = assertEquals(msg.rawPayload, "delayed message")
        _ = assert(metadataContains(msg.metadata, Map("metadata-key" -> "value")))
      } yield ()
    }
  }

  withQueue.test("published messages are processed as expected") { queueName =>
    val client = clientFixture()

    for {
      _ <- Stream
        .emits(List.range(0, 5).map(i => (i.toString, Map.empty[String, String])))
        .through(client.publish(queueName).sink(batchSize = 10))
        .compile
        .drain
      shouldAck4 <- Ref.of[IO, Boolean](false)
      res <- client
        .subscribe(queueName)
        .process[Int](5, 1.second, client.publish(queueName))((msg: Message[IO, String]) =>
          msg.rawPayload.toInt match {
            // checking various scenarios, like a message that gets reenqueue'ed once and then ok'ed,
            // a message dropped, a message failed and ack'ed, a message failed and initially not ack'ed, then ack'ed
            case 0 => Decision.Ok(0).pure[IO]
            case 1 if msg.metadata.contains("reenqueued") => Decision.Ok(1).pure[IO]
            case 1 => Decision.Reenqueue(Map("reenqueued" -> "true").some, None).pure[IO]
            case 2 => Decision.Drop.pure[IO]
            case 3 => Decision.Fail(new Throwable("3"), ack = true).pure[IO]
            case 4 => shouldAck4.getAndSet(true).map(shouldAck => Decision.Fail(new Throwable("4"), ack = shouldAck))
          })
        .take(5)
        .compile
        .toList
      _ = assertEquals(
        res.map {
          case Right(i) => i
          case Left(t) => t.getMessage.toInt
        }.sorted, // not checking the ordering, since reenqueue may influence that slightly
        List(0, 1, 3, 4, 4)
      )
    } yield ()
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

  private def metadataContains(actual: Map[String, String], expected: Map[String, String]) =
    expected.forall { case (k, v) => actual.get(k).contains(v) }

}
