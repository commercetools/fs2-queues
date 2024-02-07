package de.commercetools.queue.azure.servicebus

import cats.effect.{IO, Resource}
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode
import com.azure.messaging.servicebus.{ServiceBusClientBuilder, ServiceBusReceivedMessage}
import de.commercetools.queue.{Deserializer, MessageContext, QueueSubscriber}
import fs2.Stream
import fs2.interop.reactivestreams.fromPublisher

import scala.concurrent.duration.FiniteDuration

class ServiceBusQueueSubscriber[Data](
  name: String,
  builder: ServiceBusClientBuilder
)(implicit deserializer: Deserializer[Data])
  extends QueueSubscriber[Data] {

  override def messages(batchSize: Int, waitingTime: FiniteDuration): Stream[IO, MessageContext[Data]] =
    Stream
      .resource(Resource.make {
        IO {
          builder
            .receiver()
            .queueName(name)
            .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
            .disableAutoComplete()
            .buildAsyncClient()
        }
      } { r =>
        IO(r.close())
      })
      .flatMap { receiver =>
        fromPublisher[IO, ServiceBusReceivedMessage](receiver.receiveMessages(), 1)
          .groupWithin(batchSize, waitingTime)
          .unchunks
          .evalMapChunk { sbMessage =>
            deserializer.deserialize(sbMessage.getBody().toString()).map { data =>
              new ServiceBusMessageContext(data, sbMessage, receiver)
            }
          }
      }

}
