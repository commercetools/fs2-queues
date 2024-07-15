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
import com.commercetools.queue.{DeadletterQueueConfiguration, DeadletterQueueCreationConfiguration, QueueAdministration, QueueConfiguration, QueueCreationConfiguration}
import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.rpc.{NotFoundException, TransportChannelProvider}
import com.google.cloud.pubsub.v1.{SubscriptionAdminClient, SubscriptionAdminSettings, TopicAdminClient, TopicAdminSettings}
import com.google.protobuf.{Duration, FieldMask}
import com.google.pubsub.v1.{DeadLetterPolicy, DeleteSubscriptionRequest, DeleteTopicRequest, ExpirationPolicy, GetSubscriptionRequest, GetTopicRequest, Subscription, SubscriptionName, Topic, TopicName, UpdateSubscriptionRequest}

import scala.concurrent.duration._

class PubSubAdministration[F[_]](
  useGrpc: Boolean,
  project: String,
  channelProvider: TransportChannelProvider,
  credentials: CredentialsProvider,
  endpoint: Option[String],
  makeSubName: String => String,
  makeDLQName: String => String
)(implicit F: Async[F])
  extends QueueAdministration[F] {

  private val adminClient = Resource.fromAutoCloseable(F.delay {
    val builder =
      if (useGrpc)
        TopicAdminSettings.newBuilder()
      else
        TopicAdminSettings.newHttpJsonBuilder()
    builder
      .setCredentialsProvider(credentials)
      .setTransportChannelProvider(channelProvider)
    endpoint.foreach(builder.setEndpoint(_))
    TopicAdminClient.create(builder.build())
  })

  private val subscriptionClient = Resource.fromAutoCloseable(F.delay {
    val builder =
      if (useGrpc)
        SubscriptionAdminSettings.newBuilder()
      else
        SubscriptionAdminSettings.newHttpJsonBuilder()
    builder
      .setCredentialsProvider(credentials)
      .setTransportChannelProvider(channelProvider)
    endpoint.foreach(builder.setEndpoint(_))
    SubscriptionAdminClient.create(builder.build())
  })

  override def create(name: String, configuration: QueueCreationConfiguration): F[Unit] = {
    val topicName = TopicName.of(project, name)
    val ttl = Duration.newBuilder().setSeconds(configuration.messageTTL.toSeconds).build()
    (adminClient, subscriptionClient).tupled.use { case (adminClient, subClient) =>
      wrapFuture(F.delay {
        adminClient
          .createTopicCallable()
          .futureCall(Topic.newBuilder().setName(topicName.toString()).build())
      }) *> {
        val subBuilder = Subscription
          .newBuilder()
          .setTopic(topicName.toString())
          .setName(SubscriptionName.of(project, makeSubName(name)).toString())
          .setAckDeadlineSeconds(configuration.lockTTL.toSeconds.toInt)
          .setMessageRetentionDuration(ttl)
          // An empty expiration policy (no TTL set) ensures the subscription is never deleted
          .setExpirationPolicy(ExpirationPolicy.newBuilder().build())
        configuration.deadletter.traverse_ { case DeadletterQueueCreationConfiguration(maxAttempts) =>
          val dlTopic = TopicName.of(project, makeDLQName(name))
          // bind the dead letter topic to this subscription
          subBuilder.setDeadLetterPolicy(
            DeadLetterPolicy
              .newBuilder()
              .setDeadLetterTopic(dlTopic.toString())
              .setMaxDeliveryAttempts(maxAttempts)
              .build())
          // create the dead letter topic
          wrapFuture(F.delay {
            adminClient
              .createTopicCallable()
              .futureCall(
                Topic
                  .newBuilder()
                  .setName(dlTopic.toString())
                  // maximum retention is 31 days
                  // https://cloud.google.com/pubsub/docs/create-topic#properties_of_a_topic
                  .setMessageRetentionDuration(Duration.newBuilder().setSeconds(3600 * 24 * 31))
                  .build())
          })
        } *>
          wrapFuture(F.delay {
            subClient
              .createSubscriptionCallable()
              .futureCall(subBuilder.build())
          })
      }
    }.void
  }
    .adaptError(makeQueueException(_, name))

  override def update(name: String, messageTTL: Option[FiniteDuration], lockTTL: Option[FiniteDuration]): F[Unit] = {
    val subscriptionName = SubscriptionName.of(project, makeSubName(name))
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
    subscriptionClient.use(configuration(name, _))

  private def configuration(name: String, client: SubscriptionAdminClient): F[QueueConfiguration] =
    wrapFuture(F.delay {
      val subscriptionName = SubscriptionName.of(project, makeSubName(name))
      client
        .getSubscriptionCallable()
        .futureCall(GetSubscriptionRequest.newBuilder().setSubscription(subscriptionName.toString()).build())
    }).map { (sub: Subscription) =>
      val messageTTL =
        sub.getMessageRetentionDuration().getSeconds.seconds +
          sub.getMessageRetentionDuration().getNanos().nanos
      val lockTTL =
        sub.getAckDeadlineSeconds().seconds
      val policy = sub.getDeadLetterPolicy()
      val deadletter =
        Option(TopicName.parse(policy.getDeadLetterTopic())).map { topicName =>
          DeadletterQueueConfiguration(topicName.toString(), policy.getMaxDeliveryAttempts())
        }
      QueueConfiguration(messageTTL = messageTTL, lockTTL = lockTTL, deadletter = deadletter)
    }.adaptError(makeQueueException(_, name))

  override def delete(name: String): F[Unit] = {
    def deleteTopic(name: String, client: TopicAdminClient): F[Unit] =
      wrapFuture(F.delay {
        client
          .deleteTopicCallable()
          .futureCall(DeleteTopicRequest.newBuilder().setTopic(TopicName.of(project, name).toString()).build())
      }).void

    (adminClient, subscriptionClient).tupled.use { case (adminClient, subClient) =>
      configuration(name, subClient).flatMap { config =>
        deleteTopic(name, adminClient) *>
          wrapFuture(F.delay {
            subClient
              .deleteSubscriptionCallable()
              .futureCall(
                DeleteSubscriptionRequest
                  .newBuilder()
                  .setSubscription(SubscriptionName.of(project, makeSubName(name)).toString())
                  .build())
          }) *> config.deadletter.traverse_ { case DeadletterQueueConfiguration(dlname, _) =>
            deleteTopic(dlname, adminClient)
          }
      }
    }.void
  }.adaptError(makeQueueException(_, name))

  override def exists(name: String): F[Boolean] =
    adminClient
      .use { client =>
        wrapFuture(F.delay {
          client
            .getTopicCallable()
            .futureCall(GetTopicRequest.newBuilder().setTopic(TopicName.of(project, name).toString()).build())
        })
          .as(true)
          .recover { case _: NotFoundException =>
            false
          }
      }
      .adaptError(makeQueueException(_, name))

}
