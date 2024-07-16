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
import cats.effect.syntax.monadCancel._
import cats.syntax.functor._
import com.commercetools.queue.{MessageContext, QueuePuller, UnsealedQueuePuller}
import fs2.Chunk
import org.typelevel.otel4s.trace.Tracer

import scala.concurrent.duration.FiniteDuration

private class MeasuringQueuePuller[F[_], T](
  underlying: QueuePuller[F, T],
  metrics: QueueMetrics[F],
  tracer: Tracer[F]
)(implicit F: Temporal[F])
  extends UnsealedQueuePuller[F, T] {

  override def queueName: String = underlying.queueName

  override def pullBatch(batchSize: Int, waitingTime: FiniteDuration): F[Chunk[MessageContext[F, T]]] =
    tracer
      .span("queue.pullBatch")
      .surround {
        underlying
          .pullBatch(batchSize, waitingTime)
          .map(_.map(new MeasuringMessageContext[F, T](_, metrics, tracer)).widen[MessageContext[F, T]])
      }
      .guaranteeCase(metrics.receive)

}
