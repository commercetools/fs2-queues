package com.commercetools.queue.gcp.pubsub

import cats.effect.{Async, Resource}
import cats.syntax.all._
import com.commercetools.queue.QueueAdministration
import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.rpc.TransportChannelProvider
import com.google.cloud.pubsub.v1.{SubscriptionAdminClient, SubscriptionAdminSettings, TopicAdminClient, TopicAdminSettings}
import com.google.protobuf.Duration
import com.google.pubsub.v1.{ExpirationPolicy, Subscription, SubscriptionName, Topic, TopicName}

import scala.concurrent.duration.FiniteDuration
import com.google.pubsub.v1.DeleteTopicRequest
import com.google.pubsub.v1.DeleteSubscriptionRequest
import com.google.pubsub.v1.GetTopicRequest
import com.google.api.gax.rpc.NotFoundException

class PubSubAdministration[F[_]](
  project: String,
  channelProvider: TransportChannelProvider,
  credentials: CredentialsProvider,
  endpoint: Option[String]
)(implicit F: Async[F])
  extends QueueAdministration[F] {

  private val adminClient = Resource.fromAutoCloseable(F.delay {
    val builder = TopicAdminSettings
      .newHttpJsonBuilder()
      .setCredentialsProvider(credentials)
      .setTransportChannelProvider(channelProvider)
    endpoint.foreach(builder.setEndpoint(_))
    TopicAdminClient.create(builder.build())
  })

  private val subscriptionClient = Resource.fromAutoCloseable(F.delay {
    val builder = SubscriptionAdminSettings
      .newBuilder()
      .setCredentialsProvider(credentials)
      .setTransportChannelProvider(channelProvider)
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
          .futureCall(Topic.newBuilder().setName(topicName.toString()).setMessageRetentionDuration(ttl).build())
      })
    } *> subscriptionClient.use { client =>
      wrapFuture(F.delay {
        client
          .createSubscriptionCallable()
          .futureCall(
            Subscription
              .newBuilder()
              .setTopic(topicName.toString())
              .setName(SubscriptionName.of(project, s"fs2-queue-$name").toString())
              .setAckDeadlineSeconds(lockTTL.toSeconds.toInt)
              .setMessageRetentionDuration(ttl)
              // An empty expiration policy (no TTL set) ensures the subscription is never deleted
              .setExpirationPolicy(ExpirationPolicy.newBuilder().build())
              .build())
      })
    }.void
  }
    .adaptError(makeQueueException(_, name))

  override def update(name: String, messageTTL: Option[FiniteDuration], lockTTL: Option[FiniteDuration]): F[Unit] = ???

  override def delete(name: String): F[Unit] = {
    adminClient.use { client =>
      wrapFuture(F.delay {
        client
          .deleteTopicCallable()
          .futureCall(DeleteTopicRequest.newBuilder().setTopic(TopicName.of(project, name).toString()).build())
      })
    } *> subscriptionClient.use { client =>
      wrapFuture(F.delay {
        client
          .deleteSubscriptionCallable()
          .futureCall(
            DeleteSubscriptionRequest
              .newBuilder()
              .setSubscription(SubscriptionName.of(project, s"fs2-queue-$name").toString())
              .build())
      })
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
