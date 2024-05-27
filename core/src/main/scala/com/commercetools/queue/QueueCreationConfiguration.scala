package com.commercetools.queue

import scala.concurrent.duration.FiniteDuration

/**
 * Configuration provided upon queue creation.
 *
 * @param messageTTL the time a message is kept in the queue before being discarded
 * @param lockTTL the time a message is locked (or leased) after having been delivered
 *                to a consumer and before being made available to other again
 * @param deadletter whether to create a dead letter queue associated to this queue
 *                   with the configured amount of delivery tries before moving a message
 *                   to the dead letter queue
 */
final case class QueueCreationConfiguration(
  messageTTL: FiniteDuration,
  lockTTL: FiniteDuration,
  deadletter: Option[DeadletterQueueCreationConfiguration])
