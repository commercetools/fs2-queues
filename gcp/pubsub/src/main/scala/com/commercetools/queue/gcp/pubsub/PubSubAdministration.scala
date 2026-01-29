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

import cats.effect.instances.spawn._
import cats.effect.{Async, Resource}
import cats.syntax.all._
import com.commercetools.queue.{QueueConfiguration, UnsealedQueueAdministration}
import com.google.api.gax.core.{CredentialsProvider, ExecutorProvider}
import com.google.api.gax.rpc.{AlreadyExistsException, NotFoundException, TransportChannelProvider}
import com.google.cloud.pubsub.v1.{SubscriptionAdminClient, SubscriptionAdminSettings, TopicAdminClient, TopicAdminSettings}
import com.google.protobuf.{Duration, FieldMask}
import com.google.pubsub.v1.{DeleteSubscriptionRequest, DeleteTopicRequest, ExpirationPolicy, GetSubscriptionRequest, GetTopicRequest, Subscription, Topic, TopicName, UpdateSubscriptionRequest}

import scala.concurrent.duration._

private class PubSubAdministration[F[_]](
  project: String,
  channelProvider: TransportChannelProvider,
  credentials: CredentialsProvider,
  executorProvider: Option[ExecutorProvider],
  endpoint: Option[String],
  configs: PubSubConfig
)(implicit F: Async[F])
  extends UnsealedQueueAdministration[F] {

  private val adminClient = Resource.fromAutoCloseable(F.delay {
    val builder =
      TopicAdminSettings
        .newBuilder()
        .setCredentialsProvider(credentials)
        .setTransportChannelProvider(channelProvider)
    executorProvider.foreach(builder.setBackgroundExecutorProvider(_))
    endpoint.foreach(builder.setEndpoint(_))
    TopicAdminClient.create(builder.build())
  })

  private val subscriptionClient = Resource.fromAutoCloseable(F.delay {
    val builder =
      SubscriptionAdminSettings
        .newBuilder()
        .setCredentialsProvider(credentials)
        .setTransportChannelProvider(channelProvider)
    executorProvider.foreach(builder.setBackgroundExecutorProvider(_))
    endpoint.foreach(builder.setEndpoint(_))
    SubscriptionAdminClient.create(builder.build())
  })

  override def create(name: String, messageTTL: FiniteDuration, lockTTL: FiniteDuration): F[Unit] = {
    val topicName = TopicName.of(project, name)
    val ttl = Duration.newBuilder().setSeconds(messageTTL.toSeconds).build()
    adminClient.use { client =>
      wrapFuture(F.delay {
        client
          .createTopicCallable()
          .futureCall(Topic.newBuilder().setName(topicName.toString()).build())
      }).void.recover {
        // ignore and continue so that the subscription can be created if it's not there yet
        case _: AlreadyExistsException => ()
      }
    } *> subscriptionClient.use { client =>
      wrapFuture(F.delay {
        client
          .createSubscriptionCallable()
          .futureCall(
            Subscription
              .newBuilder()
              .setTopic(topicName.toString())
              .setName(configs.subscriptionName(project, name).toString())
              .setAckDeadlineSeconds(lockTTL.toSeconds.toInt)
              .setMessageRetentionDuration(ttl)
              // An empty expiration policy (no TTL set) ensures the subscription is never deleted
              .setExpirationPolicy(ExpirationPolicy.newBuilder().build())
              .build())
      })
    }.void
  }
    .adaptError(makeQueueException(_, name))

  override def update(name: String, messageTTL: Option[FiniteDuration], lockTTL: Option[FiniteDuration]): F[Unit] = {
    val subscriptionName = configs.subscriptionName(project, name)
    val updateSubscriptionRequest = (messageTTL, lockTTL) match {
      case (Some(messageTTL), Some(lockTTL)) =>
        val mttl = Duration.newBuilder().setSeconds(messageTTL.toSeconds).build()

        Some(
          UpdateSubscriptionRequest
            .newBuilder()
            .setSubscription(
              Subscription
                .newBuilder()
                .setName(subscriptionName.toString())
                .setMessageRetentionDuration(mttl)
                .setAckDeadlineSeconds(lockTTL.toSeconds.toInt)
                .build())
            .setUpdateMask(
              FieldMask
                .newBuilder()
                .addPaths("message_retention_duration")
                .addPaths("ack_deadline_seconds")
                .build())
            .build())
      case (Some(messageTTL), None) =>
        val mttl = Duration.newBuilder().setSeconds(messageTTL.toSeconds).build()
        Some(
          UpdateSubscriptionRequest
            .newBuilder()
            .setSubscription(
              Subscription
                .newBuilder()
                .setName(subscriptionName.toString())
                .setMessageRetentionDuration(mttl)
                .build())
            .setUpdateMask(FieldMask
              .newBuilder()
              .addPaths("message_retention_duration")
              .build())
            .build())
      case (None, Some(lockTTL)) =>
        Some(
          UpdateSubscriptionRequest
            .newBuilder()
            .setSubscription(
              Subscription
                .newBuilder()
                .setName(subscriptionName.toString())
                .setAckDeadlineSeconds(lockTTL.toSeconds.toInt)
                .build())
            .setUpdateMask(FieldMask
              .newBuilder()
              .addPaths("ack_deadline_seconds")
              .build())
            .build())
      case (None, None) =>
        None
    }
    updateSubscriptionRequest.traverse_ { req =>
      subscriptionClient.use { client =>
        wrapFuture(F.delay(client.updateSubscriptionCallable().futureCall(req)))
      }
    }
  }

  override def configuration(name: String): F[QueueConfiguration] =
    subscriptionClient.use { client =>
      wrapFuture[F, Subscription](F.delay {
        client
          .getSubscriptionCallable()
          .futureCall(
            GetSubscriptionRequest
              .newBuilder()
              .setSubscription(configs.subscriptionName(project, name).toString())
              .build())
      }).map { (sub: Subscription) =>
        val messageTTL =
          sub.getMessageRetentionDuration().getSeconds.seconds +
            sub.getMessageRetentionDuration().getNanos().nanos
        val lockTTL =
          sub.getAckDeadlineSeconds().seconds
        QueueConfiguration(messageTTL = messageTTL, lockTTL = lockTTL)
      }
    }

  override def delete(name: String): F[Unit] = {
    subscriptionClient.use { client =>
      wrapFuture(F.delay {
        client
          .deleteSubscriptionCallable()
          .futureCall(
            DeleteSubscriptionRequest
              .newBuilder()
              .setSubscription(configs.subscriptionName(project, name).toString())
              .build())
      })
    } *>
      adminClient.use { client =>
        wrapFuture(F.delay {
          client
            .deleteTopicCallable()
            .futureCall(DeleteTopicRequest.newBuilder().setTopic(TopicName.of(project, name).toString()).build())
        })
      }.void
  }.adaptError(makeQueueException(_, name))

  override def exists(name: String): F[Boolean] =
    (
      adminClient
        .use { client =>
          wrapFuture(F.delay {
            client
              .getTopicCallable()
              .futureCall(GetTopicRequest.newBuilder().setTopic(TopicName.of(project, name).toString()).build())
          })
            .as(true)
            .recover { case _: NotFoundException => false }
        }
        .adaptError(makeQueueException(_, name)),
      subscriptionClient.use { client =>
        wrapFuture(F.delay {
          client
            .getSubscriptionCallable()
            .futureCall(
              GetSubscriptionRequest
                .newBuilder()
                .setSubscription(configs.subscriptionName(project, name).toString())
                .build())
        })
          .as(true)
          .recover { case _: NotFoundException => false }
      }
    ).parMapN(_ && _)

}
