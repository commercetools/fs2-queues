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

import cats.effect.{Async, Resource}
import cats.syntax.all._
import com.commercetools.queue.{Deserializer, QueuePuller, UnsealedQueueSubscriber}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{GetQueueAttributesRequest, QueueAttributeName}

private class SQSSubscriber[F[_], T](
  val queueName: String,
  client: SqsAsyncClient,
  getQueueUrl: F[String]
)(implicit
  F: Async[F],
  deserializer: Deserializer[T])
  extends UnsealedQueueSubscriber[F, T] {

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
      .adaptError(makeQueueException(_, queueName))

  override def puller: Resource[F, QueuePuller[F, T]] =
    Resource.eval {
      for {
        queueUrl <- getQueueUrl
        lockTTL <- getLockTTL(queueUrl)
      } yield new SQSPuller(queueName, client, queueUrl, lockTTL)
    }

}
