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
import com.commercetools.queue.{Message, MessageBatch, MessageId, UnsealedMessageBatch}
import fs2.Chunk
import org.typelevel.otel4s.trace.SpanOps

private class MeasuringMessageBatch[F[_], T](
  underlying: MessageBatch[F, T],
  metrics: QueueMetrics[F],
  settleSpanOps: SpanOps[F]
)(implicit F: Temporal[F])
  extends UnsealedMessageBatch[F, T] {
  override def messages: Chunk[Message[F, T]] = underlying.messages

  /**
   * Acknowledges all the messages in the chunk. It returns a list of messageIds for which the ack operation failed.
   */
  override def ackAll: F[List[MessageId]] = metrics.ack.surround {
    settleSpanOps
      .use { span =>
        span.addAttributes(InternalMessagingAttributes.Ack) >>
          underlying.ackAll
      }
  }

  /**
   * Mark all messages from the chunk as non acknowledged. It returns a list of messageIds for which the nack operation failed..
   */
  override def nackAll: F[List[MessageId]] = metrics.nack.surround {
    settleSpanOps
      .use { span =>
        span.addAttribute(InternalMessagingAttributes.Nack) >>
          underlying.nackAll
      }
  }
}
