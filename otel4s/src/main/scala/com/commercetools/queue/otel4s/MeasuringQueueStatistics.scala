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

import cats.effect.{Resource, Temporal}
import com.commercetools.queue.{QueueStatistics, QueueStatsFetcher}
import org.typelevel.otel4s.metrics.Counter
import org.typelevel.otel4s.trace.Tracer

class MeasuringQueueStatistics[F[_]](
  underlying: QueueStatistics[F],
  requestCounter: Counter[F, Long],
  tracer: Tracer[F]
)(implicit F: Temporal[F])
  extends QueueStatistics[F] {

  override def queueName: String = underlying.queueName

  override def fetcher: Resource[F, QueueStatsFetcher[F]] =
    underlying.fetcher.map(new MeasuringQueueStatsFetcher(_, new QueueMetrics[F](queueName, requestCounter), tracer))
}
