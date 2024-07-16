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
import org.typelevel.otel4s.metrics.{Counter, Meter}
import org.typelevel.otel4s.trace.Tracer

private class MeasuringQueueClient[F[_]](
  private val underlying: QueueClient[F],
  requestCounter: Counter[F, Long],
  tracer: Tracer[F]
)(implicit F: Temporal[F])
  extends UnsealedQueueClient[F] {

  override def administration: QueueAdministration[F] =
    new MeasuringQueueAdministration[F](underlying.administration, requestCounter, tracer)

  override def statistics(name: String): QueueStatistics[F] =
    new MeasuringQueueStatistics[F](underlying.statistics(name), requestCounter, tracer)

  override def publish[T: Serializer](name: String): QueuePublisher[F, T] =
    new MeasuringQueuePublisher[F, T](underlying.publish(name), requestCounter, tracer)

  override def subscribe[T: Deserializer](name: String): QueueSubscriber[F, T] =
    new MeasuringQueueSubscriber[F, T](underlying.subscribe(name), requestCounter, tracer)

}

/** Wraps a queue client with tracing and/or metrics. */
object MeasuringQueueClient {

  final val defaultRequestMetricsName = "queue.service.call"

  /** A client tracking only metrics. */
  def metricsOnly[F[_]](
    inner: QueueClient[F],
    requestMetricsName: String = defaultRequestMetricsName
  )(implicit
    F: Temporal[F],
    meter: Meter[F]
  ): F[QueueClient[F]] =
    wrap(inner, requestMetricsName)(F = F, meter = meter, tracer = Tracer.noop)

  /** A client tracking only traces. */
  def tracesOnly[F[_]](
    inner: QueueClient[F]
  )(implicit
    F: Temporal[F],
    tracer: Tracer[F]
  ): F[QueueClient[F]] =
    wrap(inner)(F = F, meter = Meter.noop, tracer = tracer)

  /** A client tracking metrics and traces according to the provided `meter` and `tracer`. */
  def wrap[F[_]](
    inner: QueueClient[F],
    requestMetricsName: String = defaultRequestMetricsName
  )(implicit
    F: Temporal[F],
    meter: Meter[F],
    tracer: Tracer[F]
  ): F[QueueClient[F]] =
    inner match {
      case inner: MeasuringQueueClient[F] => wrap(inner.underlying)
      case _ =>
        meter
          .counter[Long](requestMetricsName)
          .withUnit("call")
          .withDescription("Counts the calls to the underlying queue service")
          .create
          .map(new MeasuringQueueClient(inner, _, tracer))
    }

}
