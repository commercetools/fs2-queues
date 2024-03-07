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

import cats.effect.kernel.Async
import cats.syntax.all._
import com.azure.messaging.servicebus.ServiceBusReceiverClient
import com.commercetools.queue.{Deserializer, MessageContext, QueuePuller}
import fs2.Chunk

import java.time.Duration
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class ServiceBusPuller[F[_], Data](
  receiver: ServiceBusReceiverClient
)(implicit
  F: Async[F],
  deserializer: Deserializer[Data])
  extends QueuePuller[F, Data] {

  override def pullBatch(batchSize: Int, waitingTime: FiniteDuration): F[Chunk[MessageContext[F, Data]]] = F
    .blocking {
      Chunk
        .iterator(receiver.receiveMessages(batchSize, Duration.ofMillis(waitingTime.toMillis)).iterator().asScala)
    }
    .flatMap { chunk =>
      chunk.traverse(sbMessage =>
        deserializer.deserialize(sbMessage.getBody().toString()).liftTo[F].map { data =>
          new ServiceBusMessageContext(data, sbMessage, receiver)
        })
    }

}