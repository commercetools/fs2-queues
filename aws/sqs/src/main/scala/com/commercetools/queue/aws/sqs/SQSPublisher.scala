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
import com.commercetools.queue.{QueuePublisher, QueuePusher, Serializer}
import software.amazon.awssdk.services.sqs.SqsAsyncClient

class SQSPublisher[F[_], T](
  client: SqsAsyncClient,
  queueName: String,
  getQueueUrl: F[String]
)(implicit
  F: Async[F],
  serializer: Serializer[T])
  extends QueuePublisher[F, T] {

  override def pusher: Resource[F, QueuePusher[F, T]] =
    Resource.eval(getQueueUrl).map(new SQSPusher(client, queueName, _))

}
