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

import cats.effect.{IO, Resource}
import com.commercetools.queue.QueueClient
import com.commercetools.queue.gcp.pubsub.{PubSubClient, PubSubConfig}
import com.commercetools.queue.testkit.QueueClientSuite
import com.google.api.gax.core.{CredentialsProvider, GoogleCredentialsProvider, NoCredentialsProvider}
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.FixedTransportChannelProvider
import com.google.cloud.pubsub.v1.{SubscriptionAdminClient, SubscriptionAdminSettings, TopicAdminClient, TopicAdminSettings}
import com.google.pubsub.v1.{GetSubscriptionRequest, TopicName}
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder

import scala.concurrent.duration.{Duration, DurationInt}
import scala.jdk.CollectionConverters._

class PubSubClientSuite extends QueueClientSuite {

  private def isEmulatorDefault = true
  private def isEmulatorEnvVar = "GCP_PUBSUB_USE_EMULATOR"

  private val testLabels: Map[String, String] = Map("project" -> "fs2-queues", "env" -> "test")

  override val queueUpdateSupported: Boolean = false // not supported
  override val inFlightMessagesStatsSupported: Boolean = false // not supported
  override val delayedMessagesStatsSupported: Boolean = false // not supported
  override val messagesStatsSupported: Boolean = // not supported in the emulator
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
      PubSubClient(project, credentials, endpoint = endpoint, configs = configs)
    }

  override def clientWithTags: Resource[IO, QueueClient[IO]] =
    config.toResource.flatMap { case (project, credentials, endpoint, configs) =>
      PubSubClient(project, credentials, endpoint = endpoint, configs = configs.copy(labels = testLabels))
    }

  private def makeChannelResource(endpoint: Option[String]): Resource[IO, GrpcTransportChannel] =
    Resource.fromAutoCloseable(IO.blocking {
      val builder = endpoint match {
        case Some(e) => NettyChannelBuilder.forTarget(e).usePlaintext()
        case None =>
          import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
          NettyChannelBuilder.forTarget("pubsub.googleapis.com:443").sslContext(GrpcSslContexts.forClient().build())
      }
      GrpcTransportChannel.create(builder.build())
    })

  private def assertTopicLabels(
    queueName: String,
    expectedLabels: Map[String, String]
  ): IO[Unit] =
    config.flatMap { case (project, credentials, endpoint, _) =>
      makeChannelResource(endpoint).use { channel =>
        val channelProvider = FixedTransportChannelProvider.create(channel)
        Resource
          .fromAutoCloseable(IO.blocking {
            val builder = TopicAdminSettings
              .newBuilder()
              .setCredentialsProvider(credentials)
              .setTransportChannelProvider(channelProvider)
            endpoint.foreach(builder.setEndpoint(_))
            TopicAdminClient.create(builder.build())
          })
          .use { topicClient =>
            IO.blocking(topicClient.getTopic(TopicName.of(project, queueName).toString()))
              .map(topic => assertEquals(topic.getLabelsMap().asScala.toMap, expectedLabels))
          }
      }
    }

  private def assertSubscriptionLabels(
    queueName: String,
    expectedLabels: Map[String, String]
  ): IO[Unit] =
    config.flatMap { case (project, credentials, endpoint, configs) =>
      makeChannelResource(endpoint).use { channel =>
        val channelProvider = FixedTransportChannelProvider.create(channel)
        Resource
          .fromAutoCloseable(IO.blocking {
            val builder = SubscriptionAdminSettings
              .newBuilder()
              .setCredentialsProvider(credentials)
              .setTransportChannelProvider(channelProvider)
            endpoint.foreach(builder.setEndpoint(_))
            SubscriptionAdminClient.create(builder.build())
          })
          .use { subClient =>
            IO.blocking(
              subClient.getSubscription(
                GetSubscriptionRequest
                  .newBuilder()
                  .setSubscription(configs.subscriptionName(project, queueName).toString())
                  .build()))
              .map(sub => assertEquals(sub.getLabelsMap().asScala.toMap, expectedLabels))
          }
      }
    }

  withQueueWithTags.test("topic/subscription should have the configured labels on creation") { queueName =>
    assertTopicLabels(queueName, testLabels) >>
      assertSubscriptionLabels(queueName, testLabels)
  }

  withQueue.test("topic should have the configured labels on update") { queueName =>
    assume(queueUpdateSupported, "this doesn't work on the emulator")
    clientWithTagsFixture().administration.update(queueName, None, None) >>
      assertTopicLabels(queueName, testLabels) >>
      assertSubscriptionLabels(queueName, testLabels)
  }
}
