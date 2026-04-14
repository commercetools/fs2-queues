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

import cats.effect.syntax.all._
import cats.effect.{Async, Poll}
import cats.syntax.all._
import com.azure.messaging.servicebus.{ServiceBusReceivedMessage, ServiceBusReceiverClient}
import com.commercetools.queue.{Deserializer, MessageBatch, MessageContext, UnsealedQueuePuller}
import fs2.Chunk

import java.time.Duration
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

private class ServiceBusPuller[F[_], Data](
  val queueName: String,
  receiver: ServiceBusReceiverClient
)(implicit
  F: Async[F],
  deserializer: Deserializer[Data])
  extends UnsealedQueuePuller[F, Data] {

  override def pullBatch(batchSize: Int, waitingTime: FiniteDuration): F[Chunk[MessageContext[F, Data]]] =
    F.uncancelable { poll =>
      pullBatchInternal(poll, batchSize, waitingTime).widen[Chunk[MessageContext[F, Data]]]
    }

  private def pullBatchInternal(poll: Poll[F], batchSize: Int, waitingTime: FiniteDuration)
    : F[Chunk[ServiceBusMessageContext[F, Data]]] = F
    .blocking {
      Chunk
        .iterator(receiver.receiveMessages(batchSize, Duration.ofMillis(waitingTime.toMillis)).iterator().asScala)
    }
    .flatMap { chunk =>
      poll {
        chunk
          .traverse { sbMessage =>
            deserializer
              .deserializeF(sbMessage.getBody().toString())
              .memoize
              .map { data =>
                new ServiceBusMessageContext(data, sbMessage, receiver)
              }
          }
      }
        .onCancel(nackAll(chunk))
    }
    .adaptError(makePullQueueException(_, queueName))

  private def nackAll(chunk: Chunk[ServiceBusReceivedMessage]): F[Unit] =
    chunk.traverse_(m => F.blocking(receiver.abandon(m)))

  override def pullMessageBatch(batchSize: Int, waitingTime: FiniteDuration): F[MessageBatch[F, Data]] =
    F.uncancelable { poll =>
      pullBatchInternal(poll, batchSize, waitingTime).map(payload =>
        new ServiceBusMessageBatch[F, Data](payload, receiver))
    }
}
