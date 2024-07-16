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

import cats.effect.{Async, Resource}
import com.azure.messaging.servicebus.ServiceBusClientBuilder
import com.commercetools.queue.{QueuePusher, Serializer, UnsealedQueuePublisher}

private class ServiceBusQueuePublisher[F[_], Data](
  val queueName: String,
  clientBuilder: ServiceBusClientBuilder
)(implicit
  F: Async[F],
  serializer: Serializer[Data])
  extends UnsealedQueuePublisher[F, Data] {

  override def pusher: Resource[F, QueuePusher[F, Data]] =
    Resource
      .make {
        F.delay(clientBuilder.sender().queueName(queueName).buildClient())
      } { s =>
        F.delay(s.close())
      }
      .map(new ServiceBusPusher(queueName, _))

}
