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
import com.commercetools.queue.{QueueStatsFetcher, UnsealedQueueStatistics}
import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.rpc.TransportChannelProvider
import com.google.cloud.monitoring.v3.stub.{GrpcMetricServiceStub, MetricServiceStubSettings}
import com.google.pubsub.v1.SubscriptionName

private class PubSubStatistics[F[_]](
  val queueName: String,
  subscriptionName: SubscriptionName,
  channelProvider: TransportChannelProvider,
  credentials: CredentialsProvider,
  endpoint: Option[String]
)(implicit F: Async[F])
  extends UnsealedQueueStatistics[F] {

  override def fetcher: Resource[F, QueueStatsFetcher[F]] =
    Resource
      .fromAutoCloseable {
        F.blocking {
          val builder = MetricServiceStubSettings
            .newBuilder()
            .setCredentialsProvider(credentials)
            .setTransportChannelProvider(channelProvider)
          endpoint.foreach(builder.setEndpoint)
          GrpcMetricServiceStub.create(builder.build())
        }
      }
      .map(new PubSubStatsFetcher(queueName, subscriptionName, _))

}
