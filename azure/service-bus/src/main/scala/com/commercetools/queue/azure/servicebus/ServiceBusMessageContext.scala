package com.commercetools.queue.azure.servicebus

import cats.effect.Async
import cats.syntax.functor._
import com.azure.messaging.servicebus.{ServiceBusReceivedMessage, ServiceBusReceiverClient}
import com.commercetools.queue.MessageContext

import java.time.Instant

class ServiceBusMessageContext[F[_], T](
  val payload: T,
  val underlying: ServiceBusReceivedMessage,
  receiver: ServiceBusReceiverClient
)(implicit F: Async[F])
  extends MessageContext[F, T] {

  override def enqueuedAt: Instant = underlying.getEnqueuedTime().toInstant()

  override def metadata: Map[String, String] =
    Map.empty

  override def ack(): F[Unit] =
    F.blocking(receiver.complete(underlying)).void

  override def nack(): F[Unit] =
    F.blocking(receiver.abandon(underlying)).void

  override def extendLock(): F[Unit] =
    F.blocking(receiver.renewMessageLock(underlying)).void

  override val messageId: String = underlying.getMessageId()

}
