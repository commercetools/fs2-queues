package com.commercetools.queue.azure.servicebus
import cats.effect.{IO, Resource}
import com.azure.messaging.servicebus.ServiceBusClientBuilder
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode
import com.commercetools.queue.{Deserializer, MessageContext, QueueSubscriber}
import fs2.{Chunk, Stream}

import java.time.Duration
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class ServiceBusQueueSubscriber[Data](
  name: String,
  builder: ServiceBusClientBuilder
)(implicit deserializer: Deserializer[Data])
  extends QueueSubscriber[Data] {

  override def messages(batchSize: Int, waitingTime: FiniteDuration): Stream[IO, MessageContext[Data]] =
    Stream
      .resource(Resource.fromAutoCloseable {
        IO {
          builder
            .receiver()
            .queueName(name)
            .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
            .disableAutoComplete()
            .buildClient()
        }
      })
      .flatMap { receiver =>
        Stream
          .repeatEval(IO.blocking(Chunk.iterator(
            receiver.receiveMessages(batchSize, Duration.ofMillis(waitingTime.toMillis)).iterator().asScala)))
          // fromPublisher[IO, ServiceBusReceivedMessage](
          //  receiver.receiveMessages().subscribeOn(Schedulers.boundedElastic()),
          //  1)
          //  .groupWithin(batchSize, waitingTime)
          .unchunks
          .evalMap { sbMessage =>
            deserializer.deserialize(sbMessage.getBody().toString()).map { data =>
              new ServiceBusMessageContext(data, sbMessage, receiver)
            }
          }
      }

}
