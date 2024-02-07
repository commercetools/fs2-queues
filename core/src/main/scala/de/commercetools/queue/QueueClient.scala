package de.commercetools.queue

import cats.effect.{IO, Resource}

/**
 * The entry point to using queues.
 * A client will manage connection pools and has knowledge of the underlying queue system.
 * A client should be managed as a resource to cleanup connections when not need anymore.
 */
trait QueueClient {

  /**
   * Gives access to adminsitrative operations.
   */
  def administration: QueueAdministration

  /**
   * Creates a publisher to the queue.
   */
  def publisher[T: Serializer](name: String): Resource[IO, QueuePublisher[T]]

  /**
   * Creates a subscriber of the queue.
   */
  def subscriber[T: Deserializer](name: String): QueueSubscriber[T]

}
