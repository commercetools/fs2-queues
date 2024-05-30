/*
 * Copyright 2024 Commercetools GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.commercetools.queue.azure.servicebus

import cats.effect.Async
import cats.syntax.functor._
import com.azure.messaging.servicebus.{ServiceBusReceivedMessage, ServiceBusReceiverClient}
import com.commercetools.queue.MessageContext

import java.time.Instant
import scala.jdk.CollectionConverters.MapHasAsScala

class ServiceBusMessageContext[F[_], T](
  val payload: F[T],
  val underlying: ServiceBusReceivedMessage,
  receiver: ServiceBusReceiverClient
)(implicit F: Async[F])
  extends MessageContext[F, T] {

  override def rawPayload: String = underlying.getBody().toString()

  override def enqueuedAt: Instant = underlying.getEnqueuedTime().toInstant()

  override def metadata: Map[String, String] =
    underlying.getRawAmqpMessage.getApplicationProperties.asScala.view.collect {
      case (k, v: String) => (k, v)
    }.toMap

  override def ack(): F[Unit] =
    F.blocking(receiver.complete(underlying)).void

  override def nack(): F[Unit] =
    F.blocking(receiver.abandon(underlying)).void

  override def extendLock(): F[Unit] =
    F.blocking(receiver.renewMessageLock(underlying)).void

  override val messageId: String = underlying.getMessageId()

}
