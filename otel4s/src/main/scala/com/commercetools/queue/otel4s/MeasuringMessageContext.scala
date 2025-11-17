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
import cats.syntax.flatMap._
import com.commercetools.queue.{MessageContext, MessageId, UnsealedMessageContext}
import org.typelevel.otel4s.trace.SpanOps

import java.time.Instant

private class MeasuringMessageContext[F[_], T](
  underlying: MessageContext[F, T],
  metrics: QueueMetrics[F],
  settleSpanOps: SpanOps[F]
)(implicit F: Temporal[F])
  extends UnsealedMessageContext[F, T] {

  override def messageId: MessageId = underlying.messageId

  override def payload: F[T] = underlying.payload

  override def rawPayload: String = underlying.rawPayload

  override def enqueuedAt: Instant = underlying.enqueuedAt

  override def metadata: Map[String, String] = underlying.metadata

  override def ack(): F[Unit] =
    metrics.ack.surround {
      settleSpanOps
        .use { span =>
          span.addAttribute(InternalMessagingAttributes.Ack) >>
            underlying.ack()
        }
    }

  override def nack(): F[Unit] =
    metrics.nack.surround {
      settleSpanOps
        .use { span =>
          span.addAttribute(InternalMessagingAttributes.Nack) >>
            underlying.nack()
        }
    }

  override def extendLock(): F[Unit] =
    metrics.extendLock.surround {
      settleSpanOps
        .use { span =>
          span.addAttribute(InternalMessagingAttributes.ExtendLock) >>
            underlying.extendLock()
        }
    }

}
