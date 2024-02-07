package de.commercetools.queue.azure.servicebus

import cats.effect.IO
import cats.syntax.all._
import com.azure.messaging.servicebus.{ServiceBusMessage, ServiceBusSenderAsyncClient}
import de.commercetools.queue.{QueuePublisher, Serializer}
import fs2.Pipe

import java.time.ZoneOffset
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class ServiceBusQueuePublisher[Data](sender: ServiceBusSenderAsyncClient)(implicit serializer: Serializer[Data])
  extends QueuePublisher[Data] {

  override def publish(message: Data, delay: Option[FiniteDuration]): IO[Unit] = {
    val sbMessage = new ServiceBusMessage(serializer.serialize(message))
    delay.traverse_(delay =>
      IO.realTimeInstant
        .map(now => sbMessage.setScheduledEnqueueTime(now.plusMillis(delay.toMillis).atOffset(ZoneOffset.UTC)))) *>
      fromBlockingMono(sender.sendMessage(sbMessage)).void
  }

  override def publish(messages: List[Data], delay: Option[FiniteDuration]): IO[Unit] = {
    val sbMessages = messages.map(msg => new ServiceBusMessage(serializer.serialize(msg)))
    fromBlockingMono(sender.sendMessages(sbMessages.asJava)).void
  }

  override def sink(chunkSize: Int): Pipe[IO, Data, Nothing] =
    _.chunkN(chunkSize).foreach { chunk =>
      publish(chunk.toList, None)
    }

}
