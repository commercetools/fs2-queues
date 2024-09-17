package com.commercetools.queue.testkit

import cats.effect.{IO, Ref}
import cats.syntax.all._
import com.commercetools.queue.{Decision, Message}
import fs2.{Chunk, Stream}
import munit.CatsEffectSuite

import scala.concurrent.duration._

/**
 * This suite tests that the features of a [[com.commercetools.queue.QueueSubscriber QueueSubscriber]] are properly
 * implemented for a concrete client.
 */
trait QueueSubscriberSuite extends CatsEffectSuite { self: QueueClientSuite =>

  withQueue.test("puller returns no messages if none is available during the configured duration") { queueName =>
    val client = clientFixture()
    client.subscribe(queueName).puller.use { puller =>
      assertIO(puller.pullBatch(10, 2.seconds), Chunk.empty)
    }
  }

  withQueue.test("puller pulls") { queueName =>
    for {
      messages <- randomMessages(30)
      client = clientFixture()
      _ <- Stream
        .emits(messages)
        .through(client.publish(queueName).sink(batchSize = 10))
        .compile
        .drain
      n <- client
        .subscribe(queueName)
        .puller
        .use(
          _.pullBatch(1, 30.seconds)
            .as(1)
            .replicateA(messages.size)
            .map(_.sum))
    } yield assertEquals(n, messages.size, "pulled messages are not as expected")
  }

  withQueue.test("puller pulls in batches") { queueName =>
    val msgNum = 30
    val batchSize = 10
    val expectedBatches = 3
    val client = clientFixture()
    for {
      _ <- Stream
        .emits(messages(msgNum))
        .through(client.publish(queueName).sink(batchSize = 10))
        .compile
        .drain
      n <- client
        .subscribe(queueName)
        .puller
        .use(
          _.pullBatch(batchSize, 30.seconds)
            .map(_.size)
            .replicateA(expectedBatches)
            .map(_.sum))
    } yield assertEquals(n, msgNum, "pulled batches are not containing all the messages")
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
          .pullBatch(1, 10.second) // waiting 10 sec, some cloud provider is really slow in non-premium plans
          .map(_.head.getOrElse(fail("expected a message, got nothing.")))
        _ = assertEquals(msg.rawPayload, "delayed message")
        _ = assert(metadataContains(msg.metadata, Map("metadata-key" -> "value")))
      } yield ()
    }
  }

  withQueue.test("processWithAutoAck receives and acks all the messages") { queueName =>
    for {
      messages <- randomMessages(30)
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
            .take(messages.size.toLong)
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
      _ <-
        if (messagesStatsSupported)
          assertIO(
            client.statistics(queueName).fetcher.use(_.fetch).map(_.messages),
            0,
            "not all the messages got acked")
        else IO.unit
    } yield ()
  }

  withQueue.test("attemptProcessWithAutoAck acks/nacks accordingly") { queueName =>
    for {
      toBeAckedRef <- Ref[IO].of(Set.empty[String])
      toBeNackedRef <- Ref[IO].of(Set.empty[String])
      client = clientFixture()
      _ <- Stream
        .emits(messages(10))
        .through(client.publish(queueName).sink(batchSize = 10))
        .compile
        .drain
      _ <- client
        .subscribe(queueName)
        .attemptProcessWithAutoAck(batchSize = 10, waitingTime = 20.seconds)(msg =>
          if (msg.rawPayload.toInt % 2 == 0) toBeAckedRef.update(_ + msg.rawPayload)
          else toBeNackedRef.update(_ + msg.rawPayload) >> IO.raiseError(new RuntimeException("failed")))
        .take(10L)
        .compile
        .drain
      toBeAcked <- toBeAckedRef.get
      toBeNacked <- toBeNackedRef.get
      _ = assertEquals(toBeAcked, Set("0", "2", "4", "6", "8"))
      _ = assertEquals(toBeNacked, Set("1", "3", "5", "7", "9"))
      _ <-
        if (messagesStatsSupported)
          assertIOBoolean(
            client
              .statistics(queueName)
              .fetcher
              .use(_.fetch)
              .map(stats => stats.messages + stats.inflight.getOrElse(0) == 5),
            "not all the expected messages got nacked"
          )
        else IO.unit
    } yield ()
  }

  withQueue.test("process respects the decision from the handler") { queueName =>
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

  private def metadataContains(actual: Map[String, String], expected: Map[String, String]) =
    expected.forall { case (k, v) => actual.get(k).contains(v) }

}
