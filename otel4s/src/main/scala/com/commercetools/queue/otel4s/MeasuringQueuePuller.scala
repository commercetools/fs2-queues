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
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.commercetools.queue.{MessageBatch, MessageContext, QueuePuller, UnsealedQueuePuller}
import fs2.Chunk
import org.typelevel.otel4s.semconv.experimental.attributes.MessagingExperimentalAttributes
import org.typelevel.otel4s.trace.{SpanBuilder, SpanOps}

import scala.concurrent.duration.FiniteDuration

private class MeasuringQueuePuller[F[_], T](
  underlying: QueuePuller[F, T],
  metrics: QueueMetrics[F],
  pullSpanOps: SpanOps[F],
  settleSpanOps: SpanOps[F],
  settleBatchSpanBuilder: SpanBuilder[F]
)(implicit F: Temporal[F])
  extends UnsealedQueuePuller[F, T] {

  override def queueName: String = underlying.queueName

  override def pullBatch(batchSize: Int, waitingTime: FiniteDuration): F[Chunk[MessageContext[F, T]]] =
    pullSpanOps
      .use { span =>
        underlying
          .pullBatch(batchSize, waitingTime)
          .flatTap(chunk =>
            span.addAttribute(MessagingExperimentalAttributes.MessagingBatchMessageCount(chunk.size.toLong)))
          .map(_.map(new MeasuringMessageContext[F, T](_, metrics, settleSpanOps))
            .widen[MessageContext[F, T]])
      }
      .guaranteeCase(metrics.receive)

  override def pullMessageBatch(batchSize: Int, waitingTime: FiniteDuration): F[MessageBatch[F, T]] =
    pullSpanOps
      .use { span =>
        underlying
          .pullMessageBatch(batchSize, waitingTime)
          .flatTap(chunk =>
            span.addAttribute(MessagingExperimentalAttributes.MessagingBatchMessageCount(chunk.messages.size.toLong)))
          .map { chunk =>
            new MeasuringMessageBatch[F, T](
              chunk,
              metrics,
              settleBatchSpanBuilder
                .addAttribute(MessagingExperimentalAttributes.MessagingBatchMessageCount(chunk.messages.size.toLong))
                .build)
          }
          .widen[MessageBatch[F, T]]
      }
      .guaranteeCase(metrics.receive)
}
