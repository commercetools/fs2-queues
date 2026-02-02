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

import cats.effect.{Async, Resource, ResourceIO, Sync}
import cats.syntax.all._
import com.commercetools.queue._
import com.google.api.gax.core.{CredentialsProvider, ExecutorProvider}
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.{FixedTransportChannelProvider, TransportChannelProvider}
import com.google.pubsub.v1.TopicName
import io.grpc.netty.shaded.io.grpc.netty.{GrpcSslContexts, NettyChannelBuilder}

private class PubSubClient[F[_]: Async] private (
  project: String,
  mkChannelProvider: Resource[F, TransportChannelProvider],
  mkMonitoringChannelProvider: Resource[F, TransportChannelProvider],
  credentials: CredentialsProvider,
  executorProvider: Option[ExecutorProvider],
  endpoint: Option[String],
  configs: PubSubConfig)
  extends UnsealedQueueClient[F] {

  def systemName: String = "gcp_pubsub"

  override def administration: QueueAdministration[F] =
    new PubSubAdministration[F](project, mkChannelProvider, credentials, executorProvider, endpoint, configs)

  override def statistics(name: String): QueueStatistics[F] =
    new PubSubStatistics(
      name,
      configs.subscriptionName(project, name),
      mkMonitoringChannelProvider,
      credentials,
      executorProvider,
      endpoint)

  override def publish[T: Serializer](name: String): QueuePublisher[F, T] =
    new PubSubPublisher[F, T](
      name,
      TopicName.of(project, name),
      mkChannelProvider,
      credentials,
      executorProvider,
      endpoint)

  override def subscribe[T: Deserializer](name: String): QueueSubscriber[F, T] =
    new PubSubSubscriber[F, T](
      name,
      configs.subscriptionName(project, name),
      mkChannelProvider,
      credentials,
      executorProvider,
      endpoint)

}

object PubSubClient {

  def apply[F[_]: Async](
    project: String,
    credentials: CredentialsProvider,
    executorProvider: Option[ExecutorProvider] = None,
    endpoint: Option[String] = None,
    mkTransportChannelProvider: Option[String] => Resource[F, TransportChannelProvider],
    mkMonitoringTransportChannelProvider: Resource[F, TransportChannelProvider],
    configs: PubSubConfig = PubSubConfig.default
  ): Resource[F, QueueClient[F]] =
    Resource.pure(
      new PubSubClient[F](
        project = project,
        mkChannelProvider = mkTransportChannelProvider(endpoint),
        mkMonitoringChannelProvider = mkMonitoringTransportChannelProvider,
        credentials = credentials,
        executorProvider = executorProvider,
        endpoint = endpoint,
        configs = configs
      )
    )

  def unmanaged[F[_]](
    project: String,
    credentials: CredentialsProvider,
    channelProvider: TransportChannelProvider,
    monitoringChannelProvider: TransportChannelProvider,
    executorProvider: Option[ExecutorProvider] = None,
    endpoint: Option[String] = None,
    configs: PubSubConfig = PubSubConfig.default
  )(implicit F: Async[F]
  ): QueueClient[F] =
    new PubSubClient[F](
      project = project,
      mkChannelProvider = Resource.pure[F, TransportChannelProvider](channelProvider),
      mkMonitoringChannelProvider = Resource.pure(monitoringChannelProvider),
      credentials = credentials,
      executorProvider = executorProvider,
      endpoint = endpoint,
      configs = configs
    )

}
