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
import cats.syntax.functor._
import cats.syntax.monadError._
import com.commercetools.queue.{MessageContext, Settlement}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{ChangeMessageVisibilityRequest, DeleteMessageRequest}

import java.time.Instant

class SQSMessageContext[F[_], T](
  val payload: T,
  val enqueuedAt: Instant,
  val metadata: Map[String, String],
  receiptHandle: String,
  val messageId: String,
  lockTTL: Int,
  queueName: String,
  queueUrl: String,
  client: SqsAsyncClient
)(implicit F: Async[F])
  extends MessageContext[F, T] {

  override def ack(): F[Unit] =
    F.fromCompletableFuture {
      F.delay {
        client.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(receiptHandle).build())
      }
    }.void
      .adaptError(makeSettlementException(_, queueName, messageId, Settlement.Ack))

  override def nack(): F[Unit] =
    F.fromCompletableFuture {
      F.delay {
        client.changeMessageVisibility(
          ChangeMessageVisibilityRequest
            .builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .visibilityTimeout(0)
            .build())
      }
    }.void
      .adaptError(makeSettlementException(_, queueName, messageId, Settlement.Nack))

  override def extendLock(): F[Unit] =
    F.fromCompletableFuture {
      F.delay {
        client.changeMessageVisibility(
          ChangeMessageVisibilityRequest
            .builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .visibilityTimeout(lockTTL)
            .build())
      }
    }.void
      .adaptError(makeSettlementException(_, queueName, messageId, Settlement.ExtendLock))

}
