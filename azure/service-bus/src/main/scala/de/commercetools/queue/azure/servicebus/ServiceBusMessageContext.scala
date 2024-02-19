package de.commercetools.queue.azure.servicebus

import cats.effect.IO
import com.azure.messaging.servicebus.{ServiceBusReceivedMessage, ServiceBusReceiverAsyncClient}
import de.commercetools.queue.MessageContext

import java.time.Instant

class ServiceBusMessageContext[T](
  val payload: T,
  val underlying: ServiceBusReceivedMessage,
  receiver: ServiceBusReceiverAsyncClient)
  extends MessageContext[T] {

  override def enqueuedAt: Instant = underlying.getEnqueuedTime().toInstant()

  override def metadata: Map[String, String] =
    Map.empty

  override def ack(): IO[Unit] =
    fromBlockingMono(receiver.complete(underlying)).void

  override def nack(): IO[Unit] =
    fromBlockingMono(receiver.abandon(underlying)).void

  override def extendLock(): IO[Unit] =
    fromBlockingMono(receiver.renewMessageLock(underlying)).void

  /**
   * Unique message identifier
   */
  override def messageId(): String = underlying.getMessageId

}
