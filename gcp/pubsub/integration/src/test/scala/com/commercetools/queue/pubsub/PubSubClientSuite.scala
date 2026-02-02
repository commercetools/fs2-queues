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

package com.commercetools.queue.pubsub

import cats.effect.{IO, Resource, Sync}
import com.commercetools.queue.QueueClient
import com.commercetools.queue.gcp.pubsub.{PubSubClient, PubSubConfig}
import com.commercetools.queue.testkit.QueueClientSuite
import com.google.api.gax.core.{CredentialsProvider, GoogleCredentialsProvider, NoCredentialsProvider}
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.{FixedTransportChannelProvider, TransportChannelProvider}
import io.grpc.netty.shaded.io.grpc.netty.{GrpcSslContexts, NettyChannelBuilder}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.jdk.CollectionConverters._

class PubSubClientSuite extends QueueClientSuite {

  private def makeDefaultTransportChannel[F[_]: Sync](
    endpoint: Option[String]
  ): Resource[F, TransportChannelProvider] =
    Resource
      .fromAutoCloseable(Sync[F].blocking {
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
      })
      .map(FixedTransportChannelProvider.create)

  private def makeDefaultMonitoringTransportChannel[F[_]](implicit F: Sync[F]): Resource[F, TransportChannelProvider] =
    Resource
      .fromAutoCloseable(F.blocking {
        GrpcTransportChannel.create(
          NettyChannelBuilder
            .forTarget("monitoring.googleapis.com:443")
            .sslContext(GrpcSslContexts.forClient().build())
            .build
        )
      })
      .map(FixedTransportChannelProvider.create)

  private def isEmulatorDefault = true
  private def isEmulatorEnvVar = "GCP_PUBSUB_USE_EMULATOR"

  override val queueUpdateSupported: Boolean = false // not supported
  override val inFlightMessagesStatsSupported: Boolean = false // not supported
  override val delayedMessagesStatsSupported: Boolean = false // not supported
  override val messagesStatsSupported: Boolean = // // not supported in the emulator
    !sys.env.get(isEmulatorEnvVar).map(_.toBoolean).getOrElse(isEmulatorDefault)

  // stats require a long time to be propagated and be available
  override def munitIOTimeout: Duration = 15.minutes

  private def config: IO[(String, CredentialsProvider, Option[String], PubSubConfig)] =
    booleanOrDefault(isEmulatorEnvVar, default = isEmulatorDefault).ifM(
      ifTrue = IO.pure(
        (
          "test-project",
          NoCredentialsProvider.create(),
          Some("localhost:8042"),
          PubSubConfig(Some("test-suite-"), Some("-sub")))),
      ifFalse = for {
        project <- string("GCP_PUBSUB_PROJECT")
        credentials = GoogleCredentialsProvider
          .newBuilder()
          .setScopesToApply(List(
            "https://www.googleapis.com/auth/pubsub", // only pubsub, full access
            "https://www.googleapis.com/auth/monitoring.read" // monitoring (for fetching stats)
          ).asJava)
          .build()
      } yield (project, credentials, None, PubSubConfig(Some("test-suite-"), Some("-sub")))
    )

  override def client: Resource[IO, QueueClient[IO]] =
    config.toResource.flatMap { case (project, credentials, endpoint, configs) =>
      PubSubClient(
        project = project,
        credentials = credentials,
        endpoint = endpoint,
        mkTransportChannelProvider = makeDefaultTransportChannel[IO],
        mkMonitoringTransportChannelProvider = makeDefaultMonitoringTransportChannel[IO],
        configs = configs
      )
    }

}
