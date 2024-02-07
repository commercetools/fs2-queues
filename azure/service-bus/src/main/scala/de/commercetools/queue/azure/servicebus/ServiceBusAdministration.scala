package de.commercetools.queue.azure.servicebus

import cats.effect.IO
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationAsyncClient
import com.azure.messaging.servicebus.administration.models.CreateQueueOptions
import de.commercetools.queue.QueueAdministration

import java.time.Duration
import scala.concurrent.duration.FiniteDuration

class ServiceBusAdministration(client: ServiceBusAdministrationAsyncClient) extends QueueAdministration {

  override def create(name: String, messageTTL: FiniteDuration, lockTTL: FiniteDuration): IO[Unit] =
    fromBlockingMono(
      client.createQueue(
        name,
        new CreateQueueOptions()
          .setDefaultMessageTimeToLive(Duration.ofMillis(messageTTL.toMillis))
          .setLockDuration(Duration.ofMillis(lockTTL.toMillis)))).void

  override def delete(name: String): IO[Unit] =
    fromBlockingMono(client.deleteQueue(name)).void

  override def exists(name: String): IO[Boolean] =
    fromBlockingMono(client.getQueueExists(name)).map(_.booleanValue)

}
