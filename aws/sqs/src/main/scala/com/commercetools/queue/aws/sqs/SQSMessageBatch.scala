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
import cats.implicits.toFunctorOps
import com.commercetools.queue.{Message, MessageBatch}
import fs2.Chunk
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{ChangeMessageVisibilityBatchRequest, ChangeMessageVisibilityBatchRequestEntry, DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry}

private class SQSMessageBatch[F[_], T](
  payload: Chunk[SQSMessageContext[F, T]],
  client: SqsAsyncClient,
  queueUrl: String
)(implicit F: Async[F])
  extends MessageBatch[F, T] {

  override def messages: Chunk[Message[F, T]] = payload

  override def ackAll: F[Unit] =
    F.fromCompletableFuture {
      F.delay {
        client.deleteMessageBatch(
          DeleteMessageBatchRequest
            .builder()
            .queueUrl(queueUrl)
            .entries(payload.map { m =>
              DeleteMessageBatchRequestEntry
                .builder()
                .receiptHandle(m.receiptHandle)
                .id(m.messageId)
                .build()
            }.asJava)
            .build()
        )
      }
    }.void

  override def nackAll: F[Unit] = F.fromCompletableFuture {
    F.delay {
      val req = ChangeMessageVisibilityBatchRequest
        .builder()
        .queueUrl(queueUrl)
        .entries(
          payload.map { m =>
            ChangeMessageVisibilityBatchRequestEntry
              .builder()
              .id(m.messageId)
              .receiptHandle(m.receiptHandle)
              .visibilityTimeout(0)
              .build()
          }.asJava
        )
        .build()
      client.changeMessageVisibilityBatch(
        req
      )
    }
  }.void
}
