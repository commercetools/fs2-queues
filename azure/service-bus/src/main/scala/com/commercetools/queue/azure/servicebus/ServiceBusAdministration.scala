package com.commercetools.queue.azure.servicebus

import cats.effect.Async
import cats.syntax.functor._
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

  override def delete(name: String): F[Unit] =
    F.blocking(client.deleteQueue(name)).void

  override def exists(name: String): F[Boolean] =
    F.blocking(client.getQueueExists(name)).map(_.booleanValue)

}
