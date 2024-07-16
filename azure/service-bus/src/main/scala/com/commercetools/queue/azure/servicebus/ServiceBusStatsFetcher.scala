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

package com.commercetools.queue.azure.servicebus

import cats.effect.Async
import cats.syntax.functor._
import cats.syntax.monadError._
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient
import com.commercetools.queue.{QueueStats, UnsealedQueueStatsFetcher}

private class ServiceBusStatsFetcher[F[_]](
  val queueName: String,
  client: ServiceBusAdministrationClient
)(implicit F: Async[F])
  extends UnsealedQueueStatsFetcher[F] {

  override def fetch: F[QueueStats] =
    F.blocking(client.getQueueRuntimeProperties(queueName))
      .map(props => QueueStats(props.getActiveMessageCount(), None, Some(props.getScheduledMessageCount())))
      .adaptError(makeQueueException(_, queueName))

}
