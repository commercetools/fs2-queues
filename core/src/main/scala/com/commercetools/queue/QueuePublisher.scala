package com.commercetools.queue

import cats.effect.IO
import fs2.Pipe

import scala.concurrent.duration.FiniteDuration

/**
 * The interface to publish to a queue.
 */
trait QueuePublisher[T] {

  /**
   * Publishes a single message to the queue, with an optional delay.
   */
  def publish(message: T, delay: Option[FiniteDuration]): IO[Unit]

  /**
   * Publishes a bunch of messages to the queue, with an optional delay.
   */
  def publish(messages: List[T], delay: Option[FiniteDuration]): IO[Unit]

  /**
   * Sink to pipe your [[fs2.Stream Stream]] into, in order to publish
   * produced data to the queue. The messages are published in batches, according
   * to the `batchSize` parameter.
   */
  def sink(batchSize: Int = 10): Pipe[IO, T, Nothing] =
    _.chunkN(batchSize).foreach { chunk =>
      publish(chunk.toList, None)
    }

}
