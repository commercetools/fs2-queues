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

import cats.effect.Async
import cats.syntax.functor._
import cats.syntax.monadError._
import com.commercetools.queue.{Action, UnsealedMessageContext}
import com.google.cloud.pubsub.v1.stub.SubscriberStub
import com.google.pubsub.v1.{AcknowledgeRequest, ModifyAckDeadlineRequest, ReceivedMessage, SubscriptionName}

import java.time.Instant
import scala.jdk.CollectionConverters._

private class PubSubMessageContext[F[_], T](
  subscriber: SubscriberStub,
  subscriptionName: SubscriptionName,
  val underlying: ReceivedMessage,
  lockDurationSeconds: Int,
  val payload: F[T],
  queueName: String
)(implicit F: Async[F])
  extends UnsealedMessageContext[F, T] {

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
