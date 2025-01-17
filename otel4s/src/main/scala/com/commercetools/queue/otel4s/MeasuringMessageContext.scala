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
import com.commercetools.queue.{MessageContext, MessageId, UnsealedMessageContext}
import org.typelevel.otel4s.trace.Tracer

import java.time.Instant

private class MeasuringMessageContext[F[_], T](
  underlying: MessageContext[F, T],
  metrics: QueueMetrics[F],
  tracer: Tracer[F]
)(implicit F: Temporal[F])
  extends UnsealedMessageContext[F, T] {

  override def messageId: MessageId = underlying.messageId

  override def payload: F[T] = underlying.payload

  override def rawPayload: String = underlying.rawPayload

  override def enqueuedAt: Instant = underlying.enqueuedAt

  override def metadata: Map[String, String] = underlying.metadata

  override def ack(): F[Unit] =
    tracer
      .span("queue.message.ack")
      .surround {
        underlying.ack()
      }
      .guaranteeCase(metrics.ack)

  override def nack(): F[Unit] =
    tracer
      .span("queue.message.nack")
      .surround {
        underlying.nack()
      }
      .guaranteeCase(metrics.nack)

  override def extendLock(): F[Unit] =
    tracer
      .span("queue.message.extendLock")
      .surround {
        underlying.extendLock()
      }
      .guaranteeCase(metrics.extendLock)

}
