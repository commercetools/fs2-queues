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
import cats.syntax.functor._
import com.commercetools.queue.{QueueStatistics, QueueStatsFetcher}
import software.amazon.awssdk.services.sqs.SqsAsyncClient

class SQSStatistics[F[_]](val queueName: String, client: SqsAsyncClient, getQueueUrl: F[String])(implicit F: Async[F])
  extends QueueStatistics[F] {

  override def fetcher: Resource[F, QueueStatsFetcher[F]] =
    Resource.eval(getQueueUrl.map(new SQSStatisticsFetcher[F](queueName, client, _)))

}
