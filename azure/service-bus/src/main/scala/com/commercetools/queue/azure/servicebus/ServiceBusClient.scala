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

  override def publisher[T: Serializer](name: String): Resource[F, QueuePublisher[F, T]] =
    for {
      sender <- Resource.make(F.delay(clientBuilder.sender().queueName(name).buildClient()))(s => F.delay(s.close()))
    } yield new ServiceBusQueuePublisher[F, T](sender)

  override def subscriber[T: Deserializer](name: String): QueueSubscriber[F, T] =
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
