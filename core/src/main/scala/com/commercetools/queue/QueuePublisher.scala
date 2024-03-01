package com.commercetools.queue

import fs2.Pipe

import scala.concurrent.duration.FiniteDuration

/**
 * The interface to publish to a queue.
 */
trait QueuePublisher[F[_], T] {

  /**
   * Publishes a single message to the queue, with an optional delay.
   */
  def publish(message: T, delay: Option[FiniteDuration]): F[Unit]

  /**
   * Publishes a bunch of messages to the queue, with an optional delay.
   */
  def publish(messages: List[T], delay: Option[FiniteDuration]): F[Unit]

  /**
   * Sink to pipe your [[fs2.Stream Stream]] into, in order to publish
   * produced data to the queue. The messages are published in batches, according
   * to the `batchSize` parameter.
   */
  def sink(batchSize: Int = 10): Pipe[F, T, Nothing] =
    _.chunkN(batchSize).foreach { chunk =>
      publish(chunk.toList, None)
    }

}
