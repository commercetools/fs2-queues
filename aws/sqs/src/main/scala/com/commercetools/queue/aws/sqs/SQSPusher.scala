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
import com.commercetools.queue.{QueuePusher, Serializer}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{SendMessageBatchRequest, SendMessageBatchRequestEntry, SendMessageRequest}

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class SQSPusher[F[_], T](
  val queueName: String,
  client: SqsAsyncClient,
  queueUrl: String
)(implicit
  serializer: Serializer[T],
  F: Async[F])
  extends QueuePusher[F, T] {

  override def push(message: T, delay: Option[FiniteDuration]): F[Unit] =
    F.fromCompletableFuture {
      F.delay {
        client.sendMessage(
          SendMessageRequest
            .builder()
            .queueUrl(queueUrl)
            .messageBody(serializer.serialize(message))
            .delaySeconds(delay.fold(0)(_.toSeconds.toInt))
            .build())
      }
    }.void

  override def push(messages: List[T], delay: Option[FiniteDuration]): F[Unit] =
    F.fromCompletableFuture {
      F.delay {
        val delaySeconds = delay.fold(0)(_.toSeconds.toInt)
        client.sendMessageBatch(
          SendMessageBatchRequest
            .builder()
            .queueUrl(queueUrl)
            .entries(messages.map { message =>
              SendMessageBatchRequestEntry
                .builder()
                .messageBody(serializer.serialize(message))
                .delaySeconds(delaySeconds)
                .build()
            }.asJava)
            .build())
      }
    }.void

}
