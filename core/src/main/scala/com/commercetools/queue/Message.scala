package com.commercetools.queue

import java.time.Instant

/**
 * Interface to access message data received from a queue.
 */
trait Message[T] {

  /**
   * Unique message identifier
   */
  def messageId: String

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

}
