package com.commercetools.queue

/**
 * @param name the name of the deadletter queue
 * @param maxAttempts the maximum number of delivery attempts made before forwarding a message to the dead letter queue
 */
final case class DeadletterQueueConfiguration(name: String, maxAttempts: Int)
