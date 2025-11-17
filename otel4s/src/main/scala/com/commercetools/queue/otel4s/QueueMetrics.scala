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

package com.commercetools.queue.otel4s

import cats.effect.Resource
import cats.effect.Resource.ExitCase
import cats.syntax.all._
import cats.{Applicative, Monad}
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.metrics.{BucketBoundaries, Counter, Histogram, Meter}
import org.typelevel.otel4s.semconv.attributes.ErrorAttributes
import org.typelevel.otel4s.semconv.experimental.attributes.MessagingExperimentalAttributes
import org.typelevel.otel4s.semconv.experimental.metrics.MessagingExperimentalMetrics

import java.util.concurrent.TimeUnit

private class QueueMetrics[F[_]: Applicative](
  commonAttributes: Attributes,
  operationDuration: Histogram[F, Double],
  sentMessages: Counter[F, Long],
  consumedMessages: Counter[F, Long],
  processDuration: Histogram[F, Double]) {

  def forQueue(name: String): QueueMetrics[F] =
    new QueueMetrics[F](
      commonAttributes.added(MessagingExperimentalAttributes.MessagingDestinationName(name)),
      operationDuration,
      sentMessages,
      consumedMessages,
      processDuration
    )

  private def buildAttributes(baseAttributes: Attributes, exitCase: Resource.ExitCase): Attributes = {
    val builder = Attributes.newBuilder

    builder ++= commonAttributes
    builder ++= baseAttributes

    exitCase match {
      case Resource.ExitCase.Succeeded => // nothing to do
      case Resource.ExitCase.Errored(t) =>
        builder.addOne(ErrorAttributes.ErrorType(t.getClass().getName()))
      case Resource.ExitCase.Canceled =>
    }

    builder.result()
  }

  val send: Resource[F, Unit] =
    operationDuration.recordDuration(
      TimeUnit.SECONDS,
      buildAttributes(Attributes(InternalMessagingAttributes.Send, InternalMessagingAttributes.SendOp), _))

  def sent(batch: Long, exitCase: ExitCase): F[Unit] =
    sentMessages.add(
      batch,
      buildAttributes(Attributes(InternalMessagingAttributes.Send, InternalMessagingAttributes.SendOp), exitCase))

  val receive: Resource[F, Unit] =
    operationDuration.recordDuration(
      TimeUnit.SECONDS,
      buildAttributes(Attributes(InternalMessagingAttributes.Receive, InternalMessagingAttributes.ReceiveOp), _))

  def consume(batch: Long): F[Unit] =
    consumedMessages.add(
      batch,
      buildAttributes(
        Attributes(InternalMessagingAttributes.Receive, InternalMessagingAttributes.ReceiveOp),
        ExitCase.Succeeded))

  val ack: Resource[F, Unit] =
    operationDuration.recordDuration(
      TimeUnit.SECONDS,
      buildAttributes(Attributes(InternalMessagingAttributes.Settle, InternalMessagingAttributes.Ack), _))

  val nack: Resource[F, Unit] =
    operationDuration.recordDuration(
      TimeUnit.SECONDS,
      buildAttributes(Attributes(InternalMessagingAttributes.Settle, InternalMessagingAttributes.Nack), _))

  val extendLock: Resource[F, Unit] =
    operationDuration.recordDuration(
      TimeUnit.SECONDS,
      buildAttributes(Attributes(InternalMessagingAttributes.Settle, InternalMessagingAttributes.ExtendLock), _))

  val process: Resource[F, Unit] =
    processDuration.recordDuration(
      TimeUnit.SECONDS,
      buildAttributes(Attributes(InternalMessagingAttributes.Process), _))

}

private object QueueMetrics {

  val OperationDurationHistogramName = "messaging.client.operation.duration"
  val SentMessagesCounterName = "messaging.client.sent.messages"
  val ConsumedMessagesCounterName = "messaging.client.consumed.messages"
  val ProcessDurationHistogramName = "messaging.process.duration"

  // these buckets are calibrated to make sense with seconds, as per the convention
  private val durationBuckets =
    BucketBoundaries(0.005d, 0.01d, 0.025d, 0.05d, 0.075d, 0.1d, 0.25d, 0.5d, 0.75d, 1d, 2.5d, 5d, 7.5d, 10d)

  def apply[F[_]: Monad](commonAttributes: Attributes)(implicit meter: Meter[F]): F[QueueMetrics[F]] =
    for {
      // see https://opentelemetry.io/docs/specs/semconv/messaging/messaging-metrics/#metric-messagingclientoperationduration
      operationDuration <- MessagingExperimentalMetrics.ClientOperationDuration.create[F, Double](durationBuckets)
      // see https://opentelemetry.io/docs/specs/semconv/messaging/messaging-metrics/#metric-messagingclientsentmessages
      sentMessages <- MessagingExperimentalMetrics.ClientSentMessages.create[F, Long]
      // see https://opentelemetry.io/docs/specs/semconv/messaging/messaging-metrics/#metric-messagingclientconsumedmessages
      consumedMessages <- MessagingExperimentalMetrics.ClientConsumedMessages.create[F, Long]
      // see https://opentelemetry.io/docs/specs/semconv/messaging/messaging-metrics/#metric-messagingprocessduration
      processDuration <- MessagingExperimentalMetrics.ProcessDuration.create[F, Double](durationBuckets)
    } yield new QueueMetrics[F](commonAttributes, operationDuration, sentMessages, consumedMessages, processDuration)

}
