package com.commercetools.queue.gcp.pubsub

import cats.effect.Async
import cats.syntax.functor._
import cats.syntax.monadError._
import com.commercetools.queue.{Action, MessageContext}
import com.google.cloud.pubsub.v1.stub.SubscriberStub
import com.google.pubsub.v1.{AcknowledgeRequest, ModifyAckDeadlineRequest, ReceivedMessage, SubscriptionName}

import java.time.Instant
import scala.jdk.CollectionConverters._

class PubSubMessageContext[F[_], T](
  subscriber: SubscriberStub,
  subscriptionName: SubscriptionName,
  underlying: ReceivedMessage,
  lockDurationSeconds: Int,
  val payload: F[T],
  queueName: String
)(implicit F: Async[F])
  extends MessageContext[F, T] {

  override def messageId: String = underlying.getAckId()

  override def rawPayload: String = underlying.getMessage().getData().toStringUtf8()

  override lazy val enqueuedAt: Instant =
    Instant.ofEpochSecond(
      underlying.getMessage().getPublishTime().getSeconds(),
      underlying.getMessage.getPublishTime().getNanos().toLong)

  override lazy val metadata: Map[String, String] =
    underlying.getMessage().getAttributesMap().asScala.toMap

  override def ack(): F[Unit] =
    wrapFuture(
      F.delay(
        subscriber
          .acknowledgeCallable()
          .futureCall(
            AcknowledgeRequest
              .newBuilder()
              .setSubscription(subscriptionName.toString())
              .addAckIds(underlying.getAckId())
              .build()))).void
      .adaptError(makeMessageException(_, queueName, messageId, Action.Ack))

  override def nack(): F[Unit] =
    wrapFuture(
      F.delay(
        subscriber
          .modifyAckDeadlineCallable()
          .futureCall(
            ModifyAckDeadlineRequest
              .newBuilder()
              .setSubscription(subscriptionName.toString())
              .setAckDeadlineSeconds(0)
              .addAckIds(underlying.getAckId())
              .build()))).void
      .adaptError(makeMessageException(_, queueName, messageId, Action.Ack))

  override def extendLock(): F[Unit] =
    wrapFuture(
      F.delay(
        subscriber
          .modifyAckDeadlineCallable()
          .futureCall(
            ModifyAckDeadlineRequest
              .newBuilder()
              .setSubscription(subscriptionName.toString())
              .setAckDeadlineSeconds(lockDurationSeconds)
              .addAckIds(underlying.getAckId())
              .build()))).void
      .adaptError(makeMessageException(_, queueName, messageId, Action.Ack))

}
