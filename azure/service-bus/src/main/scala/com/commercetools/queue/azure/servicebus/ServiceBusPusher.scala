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
import cats.syntax.all._
import com.azure.messaging.servicebus.{ServiceBusMessage, ServiceBusSenderClient}
import com.commercetools.queue.{QueuePusher, Serializer}

import java.time.ZoneOffset
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class ServiceBusPusher[F[_], Data](sender: ServiceBusSenderClient)(implicit serializer: Serializer[Data], F: Async[F])
  extends QueuePusher[F, Data] {

  override def push(message: Data, delay: Option[FiniteDuration]): F[Unit] = {
    val sbMessage = new ServiceBusMessage(serializer.serialize(message))
    delay.traverse_(delay =>
      F.realTimeInstant
        .map(now => sbMessage.setScheduledEnqueueTime(now.plusMillis(delay.toMillis).atOffset(ZoneOffset.UTC)))) *>
      F.blocking(sender.sendMessage(sbMessage)).void
  }

  override def push(messages: List[Data], delay: Option[FiniteDuration]): F[Unit] = {
    val sbMessages = messages.map(msg => new ServiceBusMessage(serializer.serialize(msg)))
    F.blocking(sender.sendMessages(sbMessages.asJava)).void
  }

}
