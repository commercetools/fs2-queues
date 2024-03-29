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

package com.commercetools.queue.aws.sqs

import cats.effect.Async
import cats.syntax.all._
import com.commercetools.queue.{Deserializer, MessageContext, QueuePuller}
import fs2.Chunk
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{MessageSystemAttributeName, ReceiveMessageRequest}

import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class SQSPuller[F[_], T](
  val queueName: String,
  client: SqsAsyncClient,
  queueUrl: String,
  lockTTL: Int
)(implicit
  F: Async[F],
  deserializer: Deserializer[T])
  extends QueuePuller[F, T] {

  override def pullBatch(batchSize: Int, waitingTime: FiniteDuration): F[Chunk[MessageContext[F, T]]] =
    F.fromCompletableFuture {
      F.delay {
        // visibility timeout is at queue creation time
        client.receiveMessage(
          ReceiveMessageRequest
            .builder()
            .queueUrl(queueUrl)
            .maxNumberOfMessages(batchSize)
            .waitTimeSeconds(waitingTime.toSeconds.toInt)
            .attributeNamesWithStrings(MessageSystemAttributeName.SENT_TIMESTAMP.toString())
            .build())
      }
    }.flatMap { response =>
      Chunk.iterator(response.messages().iterator().asScala).traverse { message =>
        deserializer.deserialize(message.body()).liftTo[F].map { payload =>
          new SQSMessageContext(
            payload,
            Instant.ofEpochMilli(message.attributes().get(MessageSystemAttributeName.SENT_TIMESTAMP).toLong),
            message.attributesAsStrings().asScala.toMap,
            message.receiptHandle(),
            message.messageId(),
            lockTTL,
            queueUrl,
            client
          )
        }
      }
    }
}
