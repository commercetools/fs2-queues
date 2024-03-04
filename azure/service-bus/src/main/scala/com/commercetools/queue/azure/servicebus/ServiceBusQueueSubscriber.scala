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
import com.azure.messaging.servicebus.ServiceBusClientBuilder
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode
import com.commercetools.queue.{Deserializer, MessageContext, QueueSubscriber}
import fs2.{Chunk, Stream}

import java.time.Duration
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class ServiceBusQueueSubscriber[F[_], Data](
  name: String,
  builder: ServiceBusClientBuilder
)(implicit
  F: Async[F],
  deserializer: Deserializer[Data])
  extends QueueSubscriber[F, Data] {

  override def messages(batchSize: Int, waitingTime: FiniteDuration): Stream[F, MessageContext[F, Data]] =
    Stream
      .resource(Resource.fromAutoCloseable {
        F.delay {
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
          .repeatEval(F.blocking(Chunk.iterator(
            receiver.receiveMessages(batchSize, Duration.ofMillis(waitingTime.toMillis)).iterator().asScala)))
          .unchunks
          .map { sbMessage =>
            deserializer.deserialize(sbMessage.getBody().toString()).map { data =>
              new ServiceBusMessageContext(data, sbMessage, receiver)
            }
          }
          .rethrow
      }

}
