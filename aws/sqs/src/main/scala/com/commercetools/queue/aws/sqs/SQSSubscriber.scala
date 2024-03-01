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
import com.commercetools.queue.{Deserializer, MessageContext, QueueSubscriber}
import fs2.{Chunk, Stream}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{GetQueueAttributesRequest, MessageSystemAttributeName, QueueAttributeName, ReceiveMessageRequest}

import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class SQSSubscriber[F[_], T](
  getQueueUrl: F[String],
  client: SqsAsyncClient
)(implicit
  F: Async[F],
  deserializer: Deserializer[T])
  extends QueueSubscriber[F, T] {

  private def getLockTTL(queueUrl: String): F[Int] =
    F.fromCompletableFuture {
      F.delay {
        client.getQueueAttributes(
          GetQueueAttributesRequest
            .builder()
            .queueUrl(queueUrl)
            .attributeNames(QueueAttributeName.VISIBILITY_TIMEOUT)
            .build())
      }
    }.map(_.attributes().get(QueueAttributeName.VISIBILITY_TIMEOUT).toInt)

  override def messages(batchSize: Int, waitingTime: FiniteDuration): Stream[F, MessageContext[F, T]] =
    Stream.eval(getQueueUrl).flatMap { queueUrl =>
      Stream.eval(getLockTTL(queueUrl)).flatMap { lockTTL =>
        Stream
          .repeatEval(F.fromCompletableFuture {
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
          })
          .map { messages =>
            Chunk.from(messages.messages().asScala)
          }
          .unchunks
          .evalMap { message =>
            for {
              sentTimestamp <- F.delay(
                Instant.ofEpochMilli(message.attributes().get(MessageSystemAttributeName.SENT_TIMESTAMP).toLong))
              data <- deserializer.deserialize(message.body()).liftTo[F]
            } yield new SQSMessageContext(
              data,
              sentTimestamp,
              message.attributesAsStrings().asScala.toMap,
              message.receiptHandle(),
              message.messageId(),
              lockTTL,
              queueUrl,
              client)
          }
      }
    }

}
