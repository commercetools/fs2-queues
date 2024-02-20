package com.commercetools.queue.azure.servicebus

import cats.effect.{IO, Resource}
import com.azure.core.credential.TokenCredential
import com.azure.core.util.ClientOptions
import com.azure.messaging.servicebus.ServiceBusClientBuilder
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder
import com.commercetools.queue.{Deserializer, QueueAdministration, QueueClient, QueuePublisher, QueueSubscriber, Serializer}

class ServiceBusClient private (
  clientBuilder: ServiceBusClientBuilder,
  adminBuilder: ServiceBusAdministrationClientBuilder)
  extends QueueClient {

  override def administration: QueueAdministration =
    new ServiceBusAdministration(adminBuilder.buildAsyncClient())

  override def publisher[T: Serializer](name: String): Resource[IO, QueuePublisher[T]] =
    for {
      sender <- Resource.make(IO(clientBuilder.sender().queueName(name).buildAsyncClient()))(s => IO(s.close()))
    } yield new ServiceBusQueuePublisher[T](sender)

  override def subscriber[T: Deserializer](name: String): QueueSubscriber[T] =
    new ServiceBusQueueSubscriber[T](name, clientBuilder)

}

object ServiceBusClient {

  def apply(namespace: String, credentials: TokenCredential, options: Option[ClientOptions] = None)
    : Resource[IO, ServiceBusClient] =
    for {
      clientBuilder <- Resource.eval {
        IO {
          val base = new ServiceBusClientBuilder().credential(namespace, credentials)
          options.fold(base)(base.clientOptions(_))
        }
      }
      adminBuilder <- Resource.eval {
        IO {
          val base = new ServiceBusAdministrationClientBuilder().credential(namespace, credentials)
          options.fold(base)(base.clientOptions(_))
        }
      }
    } yield new ServiceBusClient(clientBuilder, adminBuilder)

}
