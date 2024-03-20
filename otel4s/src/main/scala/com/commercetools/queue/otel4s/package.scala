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

package com.commercetools.queue

import cats.effect.{Outcome, Temporal}
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.{Counter, Meter}
import org.typelevel.otel4s.trace.Tracer

package object otel4s {

  private[otel4s] def handleOutcome[F[_], T](method: Attribute[String], counter: Counter[F, Long])
    : Outcome[F, Throwable, T] => F[Unit] = {
    case Outcome.Succeeded(_) =>
      counter.inc(method, Attributes.success)
    case Outcome.Errored(_) =>
      counter.inc(method, Attributes.failure)
    case Outcome.Canceled() =>
      counter.inc(method, Attributes.cancelation)
  }

  implicit class ClientOps[F[_]](val client: QueueClient[F]) extends AnyVal {

    /** A client tracking only metrics. */
    def withMetrics(
      requestMetricsName: String = MeasuringQueueClient.defaultRequestMetricsName
    )(implicit
      F: Temporal[F],
      meter: Meter[F]
    ): F[QueueClient[F]] =
      MeasuringQueueClient.metricsOnly(client, requestMetricsName)

    /** A client tracking only traces. */
    def withTraces(implicit F: Temporal[F], tracer: Tracer[F]): F[QueueClient[F]] =
      MeasuringQueueClient.tracesOnly(client)

    /** A client tracking metrics and traces according to the provided `meter` and `tracer`. */
    def withMetricsAndTraces(
      requestMetricsName: String = MeasuringQueueClient.defaultRequestMetricsName
    )(implicit
      F: Temporal[F],
      meter: Meter[F],
      tracer: Tracer[F]
    ): F[QueueClient[F]] =
      MeasuringQueueClient.wrap(client, requestMetricsName)

  }

}
