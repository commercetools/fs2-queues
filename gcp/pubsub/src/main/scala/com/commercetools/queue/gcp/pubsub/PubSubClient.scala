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
import com.commercetools.queue.{Deserializer, QueueAdministration, QueueClient, QueuePublisher, QueueStatistics, QueueSubscriber, Serializer}
import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.httpjson.{HttpJsonTransportChannel, ManagedHttpJsonChannel}
import com.google.api.gax.rpc.{FixedTransportChannelProvider, TransportChannelProvider}
import com.google.pubsub.v1.{SubscriptionName, TopicName}

class PubSubClient[F[_]: Async] private (
  project: String,
  channelProvider: TransportChannelProvider,
  useGrpc: Boolean,
  credentials: CredentialsProvider,
  endpoint: Option[String])
  extends QueueClient[F] {

  override def administration: QueueAdministration[F] =
    new PubSubAdministration[F](useGrpc, project, channelProvider, credentials, endpoint)

  override def statistics(name: String): QueueStatistics[F] =
    new PubSubStatistics(name, SubscriptionName.of(project, s"fs2-queue-$name"), channelProvider, credentials, endpoint)

  override def publish[T: Serializer](name: String): QueuePublisher[F, T] =
    new PubSubPublisher[F, T](name, useGrpc, TopicName.of(project, name), channelProvider, credentials, endpoint)

  override def subscribe[T: Deserializer](name: String): QueueSubscriber[F, T] =
    new PubSubSubscriber[F, T](
      name,
      useGrpc,
      SubscriptionName.of(project, s"fs2-queue-$name"),
      channelProvider,
      credentials,
      endpoint)

}

object PubSubClient {

  private def makeDefaultTransportChannel(endpoint: Option[String]): HttpJsonTransportChannel =
    HttpJsonTransportChannel.create(
      ManagedHttpJsonChannel.newBuilder().setEndpoint(endpoint.getOrElse("https://pubsub.googleapis.com")).build())

  def apply[F[_]](
    project: String,
    credentials: CredentialsProvider,
    endpoint: Option[String] = None,
    mkTransportChannel: Option[String] => HttpJsonTransportChannel = makeDefaultTransportChannel _
  )(implicit F: Async[F]
  ): Resource[F, PubSubClient[F]] =
    Resource
      .fromAutoCloseable(F.blocking(mkTransportChannel(endpoint)))
      .map { channel =>
        new PubSubClient[F](project, FixedTransportChannelProvider.create(channel), false, credentials, endpoint)
      }

  def unmanaged[F[_]](
    project: String,
    credentials: CredentialsProvider,
    channelProvider: TransportChannelProvider,
    useGrpc: Boolean,
    endpoint: Option[String] = None
  )(implicit F: Async[F]
  ): PubSubClient[F] =
    new PubSubClient[F](project, channelProvider, useGrpc, credentials, endpoint)

}
