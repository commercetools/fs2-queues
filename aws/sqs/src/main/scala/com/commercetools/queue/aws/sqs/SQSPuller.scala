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

import cats.effect.syntax.all._
import cats.effect.{Async, Poll}
import cats.syntax.all._
import com.commercetools.queue.{Deserializer, MessageBatch, MessageContext, MessageId, UnsealedQueuePuller}
import fs2.Chunk
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{ChangeMessageVisibilityBatchRequest, ChangeMessageVisibilityBatchRequestEntry, MessageSystemAttributeName, ReceiveMessageRequest, ReceiveMessageResponse}

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
    F.uncancelable { poll =>
      pullInternal(poll, batchSize, waitingTime).widen[Chunk[MessageContext[F, T]]]
    }

  private def pullInternal(poll: Poll[F], batchSize: Int, waitingTime: FiniteDuration)
    : F[Chunk[SQSMessageContext[F, T]]] =
    poll(F.fromCompletableFuture {
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
    }).flatMap { response =>
      poll {
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
                  messageId = MessageId(message.messageId()),
                  lockTTL = lockTTL,
                  queueName = queueName,
                  queueUrl = queueUrl,
                  client = client
                )
              }
          }
      }
        .onCancel(nackAll(response))
    }.adaptError(makePullQueueException(_, queueName))

  private def nackAll(response: ReceiveMessageResponse): F[Unit] =
    F.fromCompletableFuture {
      F.delay {
        client.changeMessageVisibilityBatch(
          ChangeMessageVisibilityBatchRequest
            .builder()
            .queueUrl(queueUrl)
            .entries(
              response
                .messages()
                .asScala
                .map { m =>
                  ChangeMessageVisibilityBatchRequestEntry
                    .builder()
                    .id(m.messageId)
                    .receiptHandle(m.receiptHandle)
                    .visibilityTimeout(0)
                    .build()
                }
                .asJava)
            .build())
      }
    }.void

  override def pullMessageBatch(batchSize: Int, waitingTime: FiniteDuration): F[MessageBatch[F, T]] =
    F.uncancelable { poll =>
      pullInternal(poll, batchSize, waitingTime)
        .map(new SQSMessageBatch[F, T](_, client, queueUrl))
    }
}
