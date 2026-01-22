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

import cats.effect.Temporal
import cats.syntax.functor._
import com.commercetools.queue.{Deserializer, QueueAdministration, QueueClient, QueuePublisher, QueueStatistics, QueueSubscriber, Serializer, UnsealedQueueClient}
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.semconv.experimental.attributes.MessagingExperimentalAttributes
import org.typelevel.otel4s.trace.Tracer

private class MeasuringQueueClient[F[_]](
  private val underlying: QueueClient[F],
  commonAttributes: Attributes,
  metrics: QueueMetrics[F],
  tracer: Tracer[F]
)(implicit F: Temporal[F])
  extends UnsealedQueueClient[F] {

  def systemName: String = underlying.systemName

  override def administration: QueueAdministration[F] =
    underlying.administration

  override def statistics(name: String): QueueStatistics[F] =
    underlying.statistics(name)

  override def publish[T: Serializer](name: String): QueuePublisher[F, T] =
    new MeasuringQueuePublisher[F, T](
      underlying.publish(name),
      metrics.forQueue(name),
      tracer,
      commonAttributes.added(MessagingExperimentalAttributes.MessagingDestinationName(name)))

  override def subscribe[T: Deserializer](name: String): QueueSubscriber[F, T] =
    new MeasuringQueueSubscriber[F, T](
      underlying.subscribe(name),
      metrics.forQueue(name),
      tracer,
      commonAttributes.added(MessagingExperimentalAttributes.MessagingDestinationName(name)))

}

/** Wraps a queue client with tracing and/or metrics. */
object MeasuringQueueClient {

  final val defaultRequestMetricsName = "queue.service.call"

  /**
   * A client tracking only metrics.
   *
   * If `fixedAttributes` is set to `true`, then the metrics always have the same number of attributes set,
   * with value `N/A` when there was otherwise no value. This can be used with legacy systems that expect metrics to always
   * have the same attributes set.
   */
  def metricsOnly[F[_]](
    inner: QueueClient[F],
    fixedAttributes: Boolean = false
  )(implicit
    F: Temporal[F],
    meter: Meter[F]
  ): F[QueueClient[F]] =
    wrap(inner, fixedAttributes)(F = F, meter = meter, tracer = Tracer.noop)

  /** A client tracking only traces. */
  def tracesOnly[F[_]](inner: QueueClient[F])(implicit F: Temporal[F], tracer: Tracer[F]): F[QueueClient[F]] =
    wrap(inner)(F = F, meter = Meter.noop, tracer = tracer)

  /**
   * A client tracking metrics and traces according to the provided `meter` and `tracer`.
   *
   * If `fixedMetricsAttributes` is set to `true`, then the metrics always have the same number of attributes set,
   * with value `N/A` when there was otherwise no value. This can be used with legacy systems that expect metrics to always
   * have the same attributes set.
   */
  def wrap[F[_]](
    inner: QueueClient[F],
    fixedMetricsAttributes: Boolean = false
  )(implicit
    F: Temporal[F],
    meter: Meter[F],
    tracer: Tracer[F]
  ): F[QueueClient[F]] =
    inner match {
      case inner: MeasuringQueueClient[F] => wrap(inner.underlying, fixedMetricsAttributes)
      case _ =>
        val commonAttributes = Attributes(MessagingExperimentalAttributes.MessagingSystem(inner.systemName))
        QueueMetrics[F](fixedMetricsAttributes, commonAttributes).map(
          new MeasuringQueueClient[F](inner, commonAttributes, _, tracer))
    }

}
