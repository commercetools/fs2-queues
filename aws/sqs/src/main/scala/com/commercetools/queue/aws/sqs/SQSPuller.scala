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
import cats.effect.syntax.concurrent._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.monadError._
import com.commercetools.queue.{Deserializer, MessageBatch, MessageContext, UnsealedQueuePuller}
import fs2.Chunk
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{MessageSystemAttributeName, ReceiveMessageRequest}

import java.time.Instant
import scala.annotation.nowarn
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

private class SQSPuller[F[_], T](
  val queueName: String,
  client: SqsAsyncClient,
  queueUrl: String,
  lockTTL: Int
)(implicit
  F: Async[F],
  deserializer: Deserializer[T])
  extends UnsealedQueuePuller[F, T] {

  override def pullBatch(batchSize: Int, waitingTime: FiniteDuration): F[Chunk[MessageContext[F, T]]] =
    pullInternal(batchSize, waitingTime).widen[Chunk[MessageContext[F, T]]]

  private def pullInternal(batchSize: Int, waitingTime: FiniteDuration): F[Chunk[SQSMessageContext[F, T]]] =
    F.fromCompletableFuture {
      F.delay {
        // visibility timeout is at queue creation time
        client.receiveMessage(
          ReceiveMessageRequest
            .builder()
            .queueUrl(queueUrl)
            .maxNumberOfMessages(batchSize)
            .waitTimeSeconds(waitingTime.toSeconds.toInt)
            .messageAttributeNames(".*")
            .attributeNamesWithStrings(MessageSystemAttributeName.SENT_TIMESTAMP.toString)
            .build(): @nowarn("msg=method attributeNamesWithStrings in trait Builder is deprecated")
        )
      }
    }.flatMap { response =>
      Chunk
        .iterator(response.messages().iterator().asScala)
        .traverse { message =>
          val body = message.body()
          deserializer
            .deserializeF(body)
            .memoize
            .map { payload =>
              new SQSMessageContext(
                payload = payload,
                rawPayload = body,
                enqueuedAt = Instant.ofEpochMilli(
                  message
                    .attributes()
                    .get(MessageSystemAttributeName.SENT_TIMESTAMP)
                    .toLong),
                metadata = message
                  .messageAttributes()
                  .asScala
                  .view
                  .collect { case (k, v) if v.dataType() == "String" => (k, v.stringValue()) }
                  .toMap,
                receiptHandle = message.receiptHandle(),
                messageId = message.messageId(),
                lockTTL = lockTTL,
                queueName = queueName,
                queueUrl = queueUrl,
                client = client
              )
            }
        }
    }.adaptError(makePullQueueException(_, queueName))

  override def pullMessageBatch(batchSize: Int, waitingTime: FiniteDuration): F[MessageBatch[F, T]] =
    pullInternal(batchSize, waitingTime)
      .map(new SQSMessageBatch[F, T](_, client, queueUrl))
}
