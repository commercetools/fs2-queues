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
import cats.syntax.all._
import com.commercetools.queue._
import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.{FixedTransportChannelProvider, TransportChannelProvider}
import com.google.pubsub.v1.{SubscriptionName, TopicName}
import io.grpc.netty.shaded.io.grpc.netty.{GrpcSslContexts, NettyChannelBuilder}

private class PubSubClient[F[_]: Async] private (
  project: String,
  channelProvider: TransportChannelProvider,
  monitoringChannelProvider: TransportChannelProvider,
  credentials: CredentialsProvider,
  endpoint: Option[String],
  configs: PubSubConfig)
  extends UnsealedQueueClient[F] {

  override def administration: QueueAdministration[F] =
    new PubSubAdministration[F](project, channelProvider, credentials, endpoint, configs)

  override def statistics(name: String): QueueStatistics[F] =
    new PubSubStatistics(
      name,
      SubscriptionName.of(project, s"${configs.subscriptionNamePrefix}$name"),
      monitoringChannelProvider,
      credentials,
      endpoint)

  override def publish[T: Serializer](name: String): QueuePublisher[F, T] =
    new PubSubPublisher[F, T](name, TopicName.of(project, name), channelProvider, credentials, endpoint)

  override def subscribe[T: Deserializer](name: String): QueueSubscriber[F, T] =
    new PubSubSubscriber[F, T](
      name,
      SubscriptionName.of(project, s"${configs.subscriptionNamePrefix}$name"),
      channelProvider,
      credentials,
      endpoint)

}

object PubSubClient {

  private def makeDefaultTransportChannel(endpoint: Option[String]): GrpcTransportChannel = {
    val builder = endpoint match {
      case Some(value) =>
        NettyChannelBuilder
          .forTarget(value)
          .usePlaintext()
      case None =>
        NettyChannelBuilder
          .forTarget("pubsub.googleapis.com:443")
          .sslContext(GrpcSslContexts.forClient().build())
    }
    GrpcTransportChannel.create(builder.build)
  }

  private def makeDefaultMonitoringTransportChannel: GrpcTransportChannel =
    GrpcTransportChannel.create(
      NettyChannelBuilder
        .forTarget("monitoring.googleapis.com:443")
        .sslContext(GrpcSslContexts.forClient().build())
        .build
    )

  def apply[F[_]](
    project: String,
    credentials: CredentialsProvider,
    endpoint: Option[String] = None,
    mkTransportChannel: Option[String] => GrpcTransportChannel = makeDefaultTransportChannel,
    mkMonitoringTransportChannel: => GrpcTransportChannel = makeDefaultMonitoringTransportChannel,
    configs: PubSubConfig = PubSubConfig.default
  )(implicit F: Async[F]
  ): Resource[F, QueueClient[F]] =
    (
      Resource.fromAutoCloseable(F.blocking(mkTransportChannel(endpoint))),
      Resource.fromAutoCloseable(F.blocking(mkMonitoringTransportChannel)))
      .mapN { (channel, monitoringChannel) =>
        new PubSubClient[F](
          project = project,
          channelProvider = FixedTransportChannelProvider.create(channel),
          monitoringChannelProvider = FixedTransportChannelProvider.create(monitoringChannel),
          credentials = credentials,
          endpoint = endpoint,
          configs = configs
        )
      }

  def unmanaged[F[_]](
    project: String,
    credentials: CredentialsProvider,
    channelProvider: TransportChannelProvider,
    monitoringChannelProvider: TransportChannelProvider,
    endpoint: Option[String] = None,
    configs: PubSubConfig = PubSubConfig.default
  )(implicit F: Async[F]
  ): QueueClient[F] =
    new PubSubClient[F](
      project = project,
      channelProvider = channelProvider,
      monitoringChannelProvider = monitoringChannelProvider,
      credentials = credentials,
      endpoint = endpoint,
      configs = configs
    )

}
