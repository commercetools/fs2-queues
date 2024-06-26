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
import cats.syntax.monadError._
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient
import com.azure.messaging.servicebus.administration.models.CreateQueueOptions
import com.commercetools.queue.{DeadletterQueueConfiguration, QueueAdministration, QueueConfiguration, QueueCreationConfiguration}

import java.time.Duration
import scala.concurrent.duration._

class ServiceBusAdministration[F[_]](client: ServiceBusAdministrationClient)(implicit F: Async[F])
  extends QueueAdministration[F] {

  override def create(name: String, configuration: QueueCreationConfiguration): F[Unit] =
    F.blocking {
      val options = new CreateQueueOptions()
        .setDefaultMessageTimeToLive(Duration.ofMillis(configuration.messageTTL.toMillis))
        .setLockDuration(Duration.ofMillis(configuration.lockTTL.toMillis))
      configuration.deadletter match {
        case Some(configuration) =>
          options
            .setMaxDeliveryCount(configuration.maxAttempts)
            .setDeadLetteringOnMessageExpiration(true)
        case None =>
          options
            .setMaxDeliveryCount(Int.MaxValue)
            .setDeadLetteringOnMessageExpiration(false)
      }
      client.createQueue(
        name,
        options
      )
    }.void
      .adaptError(makeQueueException(_, name))

  override def update(name: String, messageTTL: Option[FiniteDuration], lockTTL: Option[FiniteDuration]): F[Unit] =
    F.blocking {
      val properties = client.getQueue(name)
      messageTTL.foreach(ttl => properties.setDefaultMessageTimeToLive(Duration.ofMillis(ttl.toMillis)))
      lockTTL.foreach(ttl => properties.setLockDuration(Duration.ofMillis(ttl.toMillis)))
      val _ = client.updateQueue(properties)
    }.adaptError(makeQueueException(_, name))

  override def configuration(name: String): F[QueueConfiguration] =
    F.blocking {
      val properties = client.getQueue(name)
      val messageTTL = properties.getDefaultMessageTimeToLive().toMillis.millis
      val lockTTL = properties.getLockDuration().toMillis.millis
      val deadletter =
        Option.when(properties.isDeadLetteringOnMessageExpiration())(
          DeadletterQueueConfiguration(properties.getForwardDeadLetteredMessagesTo(), properties.getMaxDeliveryCount()))

      QueueConfiguration(messageTTL = messageTTL, lockTTL = lockTTL, deadletter = deadletter)
    }.adaptError(makeQueueException(_, name))

  override def delete(name: String): F[Unit] =
    F.blocking(client.deleteQueue(name))
      .void
      .adaptError(makeQueueException(_, name))

  override def exists(name: String): F[Boolean] =
    F.blocking(client.getQueueExists(name))
      .map(_.booleanValue)
      .adaptError(makeQueueException(_, name))

}
