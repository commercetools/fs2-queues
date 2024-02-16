package de.commercetools.queue

import cats.effect.IO

import java.time
import java.time.Instant
import scala.concurrent.duration._

/**
 * Interface to interact with a message received from a queue.
 * The messages must be explicitly aknowledged after having been processed.
 */
trait MessageContext[T] {

  /**
   * The message payload
   */
  def payload: T

  /**
   * When the message was put into the queue.
   */
  def enqueuedAt: Instant

  /**
   * Raw message metadata (depending on the underlying queue system).
   */
  def metadata: Map[String, String]

  /**
   * Acknowledges the message. It will be removed from the queue, so that
   * no other subscriber will process it.
   */
  def ack(): IO[Unit]

  /**
   * Marks the the message as non acknowledged, for instance in case of an error
   * in processing. The message will be unlocked and available for processing by
   * other subscribers.
   */
  def nack(): IO[Unit]

  /**
   * Extends the lock for this message. It gives this processor more time to process
   * it, for instance. The duration by which the lock is extended is either a queue
   * configured duration or on that is configured in the client, depending on the
   * underlying queue system.
   */
  def extendLock(): IO[Unit]

  /**
   * Returns for how long the message has been in the queue.
   */
  def enqueuedFor(): IO[FiniteDuration] =
    IO.realTimeInstant.map { now =>
      time.Duration.between(enqueuedAt, now).toMillis().millis
    }

  /**
   * Unique message identifier
   */
  def messageId(): String

}
