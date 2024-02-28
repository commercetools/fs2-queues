package com.commercetools.queue.azure.servicebus

import cats.effect.IO
import cats.syntax.all._
import com.azure.messaging.servicebus.{ServiceBusMessage, ServiceBusSenderClient}
import com.commercetools.queue.{QueuePublisher, Serializer}

import java.time.ZoneOffset
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class ServiceBusQueuePublisher[Data](sender: ServiceBusSenderClient)(implicit serializer: Serializer[Data])
  extends QueuePublisher[Data] {

  override def publish(message: Data, delay: Option[FiniteDuration]): IO[Unit] = {
    val sbMessage = new ServiceBusMessage(serializer.serialize(message))
    delay.traverse_(delay =>
      IO.realTimeInstant
        .map(now => sbMessage.setScheduledEnqueueTime(now.plusMillis(delay.toMillis).atOffset(ZoneOffset.UTC)))) *>
      IO.blocking(sender.sendMessage(sbMessage)).void
  }

  override def publish(messages: List[Data], delay: Option[FiniteDuration]): IO[Unit] = {
    val sbMessages = messages.map(msg => new ServiceBusMessage(serializer.serialize(msg)))
    IO.blocking(sender.sendMessages(sbMessages.asJava)).void
  }

}
