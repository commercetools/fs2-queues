package com.commercetools.queue.testkit

import cats.syntax.all._
import fs2.Stream
import munit.CatsEffectSuite

import scala.concurrent.duration.DurationInt

/**
 * This suite tests that the features of a [[com.commercetools.queue.QueueStatistics QueueStatistics]] are properly
 * implemented for a concrete client.
 */
trait QueueStatisticsSuite extends CatsEffectSuite { self: QueueClientSuite =>

  withQueue.test("stats should report queued messages") { queueName =>
    assume(messagesStatsSupported)
    for {
      messages <- randomMessages(30)
      client = clientFixture()
      _ <- Stream
        .emits(messages)
        .through(client.publish(queueName).sink(batchSize = 10))
        .compile
        .drain
      _ <- client
        .statistics(queueName)
        .fetcher
        .use { fetcher =>
          eventuallyIO(fetcher.fetch.map(_.messages), messages.size, "Queue should be full")
        }
    } yield ()
  }

  withQueue.test("stats should report inflight messages") { queueName =>
    assume(messagesStatsSupported && inFlightMessagesStatsSupported)
    for {
      messages <- randomMessages(30)
      client = clientFixture()
      _ <- Stream
        .emits(messages)
        .through(client.publish(queueName).sink(batchSize = 10))
        .compile
        .drain
      _ <- (client.subscribe(queueName).puller, client.statistics(queueName).fetcher).tupled
        .use { case (puller, statsFetcher) =>
          for {
            chunk <- puller.pullBatch(10, 10.seconds)
            _ <- eventuallyIO(
              statsFetcher.fetch.map(_.inflight),
              chunk.size.some,
              "Inflight stats doesn't match pulled messages")
          } yield ()
        }
    } yield ()
  }

  withQueue.test("stats should report delayed messages") { queueName =>
    assume(messagesStatsSupported && delayedMessagesStatsSupported)
    for {
      messages <- randomMessages(30)
      client = clientFixture()
      _ <- Stream
        .emits(messages) // putting a really long delay so that the test can pass even in slow envs
        .through(client.publish(queueName).sink(batchSize = 10, delay = 1.minute.some))
        .compile
        .drain
      _ <- client
        .statistics(queueName)
        .fetcher
        .use { statsFetcher =>
          eventuallyIO(
            statsFetcher.fetch.map(_.delayed),
            messages.size.some,
            "Delayed stats doesn't match pulled messages")
        }
    } yield ()
  }

}
