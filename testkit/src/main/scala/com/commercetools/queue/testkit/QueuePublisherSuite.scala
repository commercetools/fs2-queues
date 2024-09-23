package com.commercetools.queue.testkit

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
    assume(messagesStatsSupported)
    val client = clientFixture()
    for {
      msgs <- randomMessages(30)
      _ <- Stream
        .emits(msgs)
        .through(client.publish(queueName).sink(batchSize = 10))
        .compile
        .drain
      messagesInQueue <- client.statistics(queueName).fetcher.use(_.fetch).map(_.messages)
      _ = assertEquals(messagesInQueue, msgs.size)
    } yield ()
  }

  withQueue.test("sink publishes all the messages with a delay") { queueName =>
    assume(messagesStatsSupported && delayedMessagesStatsSupported)
    val client = clientFixture()
    for {
      msgs <- randomMessages(30)
      _ <- Stream
        .emits(msgs)
        .through(client.publish(queueName).sink(batchSize = 10, delay = 1.minute.some))
        .compile
        .drain
      statsFetcher = client.statistics(queueName).fetcher
      messagesInQueue <- statsFetcher.use(_.fetch).map(_.messages)
      delayedMessages <- statsFetcher.use(_.fetch).map(_.delayed)
      _ = assertEquals(delayedMessages, msgs.size.some, "delayed messages are not what we expect")
      _ = assertEquals(messagesInQueue, 0, "the queue is not empty")
    } yield ()
  }

}
