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

import cats.effect.MonadCancel
import cats.effect.syntax.monadCancel._
import com.commercetools.queue.{QueueStats, QueueStatsFetcher, UnsealedQueueStatsFetcher}
import org.typelevel.otel4s.trace.Tracer

private class MeasuringQueueStatsFetcher[F[_]](
  underlying: QueueStatsFetcher[F],
  metrics: QueueMetrics[F],
  tracer: Tracer[F]
)(implicit F: MonadCancel[F, Throwable])
  extends UnsealedQueueStatsFetcher[F] {

  override def fetch: F[QueueStats] =
    tracer
      .span("queue.fetchStats")
      .surround {
        underlying.fetch
      }
      .guaranteeCase(metrics.stats)

}
