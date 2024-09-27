package com.commercetools.queue.testkit

import cats.effect.IO
import cats.effect.kernel.Ref
import munit.CatsEffectSuite
import fs2.Stream
import cats.syntax.all._

import scala.concurrent.duration.DurationInt

/**
 * This suite tests that the features of a [[com.commercetools.queue.QueuePublisher QueuePublisher]] are properly
 * implemented for a concrete client.
 */
trait QueuePublisherSuite extends CatsEffectSuite { self: QueueClientSuite =>

  withQueue.test("sink publishes all the messages") { queueName =>
    val client = clientFixture()
    for {
      msgs <- randomMessages(10)
      count <- Ref.of[IO, Int](0)
      _ <- Stream
        .emits(msgs)
        .through(client.publish(queueName).sink(batchSize = 10))
        .compile
        .drain
      _ <- client
        .subscribe(queueName)
        .processWithAutoAck(10, waitingTime)(_ => count.update(_ + 1))
        .take(msgs.size.toLong)
        .compile
        .drain
        .timeout(30.seconds)
      _ <- assertIO(count.get, msgs.size)
    } yield ()
  }

  withQueue.test("sink publishes all the messages with a delay") { queueName =>
    val client = clientFixture()
    for {
      msgs <- randomMessages(10)
      _ <- Stream
        .emits(msgs)
        .through(client.publish(queueName).sink(batchSize = 10, delay = 10.seconds.some))
        .compile
        .drain
      _ <- client
        .subscribe(queueName)
        .puller
        .use(puller =>
          for {
            _ <- eventuallyBoolean(
              puller.pullBatch(1, 1.second).map(_.isEmpty),
              "chunk is not empty, messages are not getting delayed")
            _ <- IO.sleep(10.seconds)
            _ <- eventuallyBoolean(
              puller.pullBatch(1, waitingTime).map(chunk => !chunk.isEmpty),
              "got no messages after delay")
          } yield ())
    } yield ()
  }

}
