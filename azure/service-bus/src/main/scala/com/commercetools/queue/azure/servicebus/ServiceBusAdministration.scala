package com.commercetools.queue.azure.servicebus

import cats.effect.IO
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient
import com.azure.messaging.servicebus.administration.models.CreateQueueOptions
import com.commercetools.queue.QueueAdministration

import java.time.Duration
import scala.concurrent.duration.FiniteDuration

class ServiceBusAdministration(client: ServiceBusAdministrationClient) extends QueueAdministration {

  override def create(name: String, messageTTL: FiniteDuration, lockTTL: FiniteDuration): IO[Unit] =
    IO.blocking(
      client.createQueue(
        name,
        new CreateQueueOptions()
          .setDefaultMessageTimeToLive(Duration.ofMillis(messageTTL.toMillis))
          .setLockDuration(Duration.ofMillis(lockTTL.toMillis))))
      .void

  override def delete(name: String): IO[Unit] =
    IO.blocking(client.deleteQueue(name)).void

  override def exists(name: String): IO[Boolean] =
    IO.blocking(client.getQueueExists(name)).map(_.booleanValue)

}
