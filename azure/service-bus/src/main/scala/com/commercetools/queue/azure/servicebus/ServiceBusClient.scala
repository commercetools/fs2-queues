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

import cats.effect.{Async, Resource}
import com.azure.core.credential.TokenCredential
import com.azure.core.util.ClientOptions
import com.azure.messaging.servicebus.ServiceBusClientBuilder
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder
import com.commercetools.queue.{Deserializer, QueueAdministration, QueueClient, QueuePublisher, QueueSubscriber, Serializer}

class ServiceBusClient[F[_]] private (
  clientBuilder: ServiceBusClientBuilder,
  adminBuilder: ServiceBusAdministrationClientBuilder
)(implicit F: Async[F])
  extends QueueClient[F] {

  override def administration: QueueAdministration[F] =
    new ServiceBusAdministration(adminBuilder.buildClient())

  override def publish[T: Serializer](name: String): QueuePublisher[F, T] =
    new ServiceBusQueuePublisher[F, T](clientBuilder, name)

  override def subscribe[T: Deserializer](name: String): QueueSubscriber[F, T] =
    new ServiceBusQueueSubscriber[F, T](name, clientBuilder)

}

object ServiceBusClient {

  def apply[F[_]](connectionString: String)(implicit F: Async[F]): Resource[F, ServiceBusClient[F]] =
    for {
      clientBuilder <- Resource.eval {
        F.delay {
          new ServiceBusClientBuilder().connectionString(connectionString)
        }
      }
      adminBuilder <- Resource.eval {
        F.delay {
          new ServiceBusAdministrationClientBuilder()
            .connectionString(connectionString)
        }
      }
    } yield new ServiceBusClient(clientBuilder, adminBuilder)

  def apply[F[_]](
    namespace: String,
    credentials: TokenCredential,
    options: Option[ClientOptions] = None
  )(implicit F: Async[F]
  ): Resource[F, ServiceBusClient[F]] =
    for {
      clientBuilder <- Resource.eval {
        F.delay {
          val base = new ServiceBusClientBuilder().credential(namespace, credentials)
          options.fold(base)(base.clientOptions(_))
        }
      }
      adminBuilder <- Resource.eval {
        F.delay {
          val base = new ServiceBusAdministrationClientBuilder().credential(namespace, credentials)
          options.fold(base)(base.clientOptions(_))
        }
      }
    } yield new ServiceBusClient(clientBuilder, adminBuilder)

}
