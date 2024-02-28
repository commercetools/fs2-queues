package com.commercetools.queue.azure.servicebus

import cats.effect.IO
import com.azure.messaging.servicebus.{ServiceBusReceivedMessage, ServiceBusReceiverClient}
import com.commercetools.queue.MessageContext

import java.time.Instant

class ServiceBusMessageContext[T](
  val payload: T,
  val underlying: ServiceBusReceivedMessage,
  receiver: ServiceBusReceiverClient)
  extends MessageContext[T] {

  override def enqueuedAt: Instant = underlying.getEnqueuedTime().toInstant()

  override def metadata: Map[String, String] =
    Map.empty

  override def ack(): IO[Unit] =
    IO.blocking(receiver.complete(underlying)).void

  override def nack(): IO[Unit] =
    IO.blocking(receiver.abandon(underlying)).void

  override def extendLock(): IO[Unit] =
    IO.blocking(receiver.renewMessageLock(underlying)).void

  override val messageId: String = underlying.getMessageId()

}
