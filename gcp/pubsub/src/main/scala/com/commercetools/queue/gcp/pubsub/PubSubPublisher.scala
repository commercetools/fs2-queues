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
import com.commercetools.queue.{QueuePusher, Serializer, UnsealedQueuePublisher}
import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.rpc.TransportChannelProvider
import com.google.cloud.pubsub.v1.stub.{GrpcPublisherStub, HttpJsonPublisherStub, PublisherStubSettings}
import com.google.pubsub.v1.TopicName

class PubSubPublisher[F[_], T](
  val queueName: String,
  useGrpc: Boolean,
  topicName: TopicName,
  channelProvider: TransportChannelProvider,
  credentials: CredentialsProvider,
  endpoint: Option[String]
)(implicit
  F: Async[F],
  serializer: Serializer[T])
  extends UnsealedQueuePublisher[F, T] {

  override def pusher: Resource[F, QueuePusher[F, T]] =
    Resource
      .fromAutoCloseable {
        F.blocking {
          val builder =
            if (useGrpc)
              PublisherStubSettings.newBuilder()
            else
              PublisherStubSettings.newHttpJsonBuilder()
          builder
            .setCredentialsProvider(credentials)
            .setTransportChannelProvider(channelProvider)
          endpoint.foreach(builder.setEndpoint(_))
          if (useGrpc)
            GrpcPublisherStub.create(builder.build())
          else
            HttpJsonPublisherStub.create(builder.build())

        }
      }
      .map(new PubSubPusher[F, T](queueName, topicName, _))

}
