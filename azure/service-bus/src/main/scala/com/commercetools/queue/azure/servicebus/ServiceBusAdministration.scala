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
import com.commercetools.queue.QueueAdministration

import java.time.Duration
import scala.concurrent.duration.FiniteDuration

class ServiceBusAdministration[F[_]](client: ServiceBusAdministrationClient)(implicit F: Async[F])
  extends QueueAdministration[F] {

  override def create(name: String, messageTTL: FiniteDuration, lockTTL: FiniteDuration): F[Unit] =
    F.blocking(
      client.createQueue(
        name,
        new CreateQueueOptions()
          .setDefaultMessageTimeToLive(Duration.ofMillis(messageTTL.toMillis))
          .setLockDuration(Duration.ofMillis(lockTTL.toMillis))))
      .void
      .adaptError(makeQueueException(_, name))

  override def update(name: String, messageTTL: Option[FiniteDuration], lockTTL: Option[FiniteDuration]): F[Unit] =
    F.blocking {
      val properties = client.getQueue(name)
      messageTTL.foreach(ttl => properties.setDefaultMessageTimeToLive(Duration.ofMillis(ttl.toMillis)))
      lockTTL.foreach(ttl => properties.setLockDuration(Duration.ofMillis(ttl.toMillis)))
      val _ = client.updateQueue(properties)
    }

  override def delete(name: String): F[Unit] =
    F.blocking(client.deleteQueue(name))
      .void
      .adaptError(makeQueueException(_, name))

  override def exists(name: String): F[Boolean] =
    F.blocking(client.getQueueExists(name))
      .map(_.booleanValue)
      .adaptError(makeQueueException(_, name))

}
