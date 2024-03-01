package com.commercetools.queue

import cats.effect.Resource

/**
 * The entry point to using queues.
 * A client will manage connection pools and has knowledge of the underlying queue system.
 * A client should be managed as a resource to cleanup connections when not need anymore.
 */
trait QueueClient[F[_]] {

  /**
   * Gives access to adminsitrative operations.
   */
  def administration: QueueAdministration[F]

  /**
   * Creates a publisher to the queue.
   */
  def publisher[T: Serializer](name: String): Resource[F, QueuePublisher[F, T]]

  /**
   * Creates a subscriber of the queue.
   */
  def subscriber[T: Deserializer](name: String): QueueSubscriber[F, T]

}
