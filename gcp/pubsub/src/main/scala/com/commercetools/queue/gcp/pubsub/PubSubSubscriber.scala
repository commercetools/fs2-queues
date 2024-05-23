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

package com.commercetools.queue.gcp.pubsub

import cats.effect.{Async, Resource}
import cats.syntax.functor._
import com.commercetools.queue.{Deserializer, QueuePuller, QueueSubscriber}
import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.rpc.TransportChannelProvider
import com.google.cloud.pubsub.v1.stub.{HttpJsonSubscriberStub, SubscriberStubSettings}
import com.google.pubsub.v1.{GetSubscriptionRequest, SubscriptionName}

class PubSubSubscriber[F[_], T](
  val queueName: String,
  subscriptionName: SubscriptionName,
  channelProvider: TransportChannelProvider,
  credentials: CredentialsProvider,
  endpoint: Option[String]
)(implicit
  F: Async[F],
  deserializer: Deserializer[T])
  extends QueueSubscriber[F, T] {

  override def puller: Resource[F, QueuePuller[F, T]] =
    Resource
      .fromAutoCloseable {
        F.blocking {
          val builder =
            SubscriberStubSettings
              .newHttpJsonBuilder()
              .setCredentialsProvider(credentials)
              .setTransportChannelProvider(channelProvider)
          endpoint.foreach(builder.setEndpoint(_))
          HttpJsonSubscriberStub.create(builder.build())
        }
      }
      .evalMap { subscriber =>
        wrapFuture(
          F.delay(subscriber
            .getSubscriptionCallable()
            .futureCall(GetSubscriptionRequest.newBuilder().setSubscription(subscriptionName.toString()).build()))).map(
          sub => (subscriber, sub))
      }
      .map { case (subscriber, subscription) =>
        new PubSubPuller[F, T](queueName, subscriptionName, subscriber, subscription.getAckDeadlineSeconds())
      }

}
