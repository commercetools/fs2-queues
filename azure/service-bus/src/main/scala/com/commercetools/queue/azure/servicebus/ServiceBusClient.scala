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
import com.commercetools.queue.{Deserializer, QueueAdministration, QueuePublisher, QueueStatistics, QueueSubscriber, Serializer, UnsealedQueueClient}

class ServiceBusClient[F[_]] private (
  clientBuilder: ServiceBusClientBuilder,
  adminBuilder: ServiceBusAdministrationClientBuilder,
  newQueueSettings: NewQueueSettings
)(implicit F: Async[F])
  extends UnsealedQueueClient[F] {

  override def administration: QueueAdministration[F] =
    new ServiceBusAdministration(adminBuilder.buildClient(), newQueueSettings)

  override def statistics(name: String): QueueStatistics[F] =
    new ServiceBusStatistics(name, adminBuilder)

  override def publish[T: Serializer](name: String): QueuePublisher[F, T] =
    new ServiceBusQueuePublisher[F, T](name, clientBuilder)

  override def subscribe[T: Deserializer](name: String): QueueSubscriber[F, T] =
    new ServiceBusQueueSubscriber[F, T](name, clientBuilder)

}

object ServiceBusClient {

  /**
   * Creates a new client from the connection string.
   * You can optionally provide global settings for when queues are created via the library.
   */
  def fromConnectionString[F[_]](
    connectionString: String,
    newQueueSettings: NewQueueSettings = NewQueueSettings.default
  )(implicit F: Async[F]
  ): Resource[F, ServiceBusClient[F]] =
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
    } yield new ServiceBusClient(clientBuilder, adminBuilder, newQueueSettings)

  /**
   * Creates a new client for the given namespace and some credentials.
   * You can optionally provide global settings for when queues are created via the library.
   */
  def apply[F[_]](
    namespace: String,
    credentials: TokenCredential,
    newQueueSettings: NewQueueSettings = NewQueueSettings.default,
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
    } yield new ServiceBusClient(clientBuilder, adminBuilder, newQueueSettings)

  /**
   * Creates a new client wrapping unmanaged SDK client.
   * This is useful when integrating the library in a codebase that already manages a Java SDK client.
   * Otherwise, prefer the other variants to construct a client.
   *
   * You can optionally provide global settings for when queues are created via the library.
   */
  def unmanaged[F[_]](
    clientBuilder: ServiceBusClientBuilder,
    adminBuilder: ServiceBusAdministrationClientBuilder,
    newQueueSettings: NewQueueSettings = NewQueueSettings.default
  )(implicit F: Async[F]
  ): ServiceBusClient[F] =
    new ServiceBusClient(clientBuilder, adminBuilder, newQueueSettings)

}
