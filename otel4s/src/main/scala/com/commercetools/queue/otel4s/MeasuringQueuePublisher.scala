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

import cats.effect.{MonadCancel, Resource}
import com.commercetools.queue.{QueuePublisher, QueuePusher, UnsealedQueuePublisher}
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.metrics.Counter
import org.typelevel.otel4s.trace.{SpanKind, Tracer}

private class MeasuringQueuePublisher[F[_], T](
  underlying: QueuePublisher[F, T],
  requestCounter: Counter[F, Long],
  tracer: Tracer[F],
  commonAttributes: Attributes
)(implicit F: MonadCancel[F, Throwable])
  extends UnsealedQueuePublisher[F, T] {

  override def queueName: String = underlying.queueName

  // used by all pushers created by this publisher
  private val pushSpanOps = tracer
    .spanBuilder(s"send $queueName")
    .withSpanKind(SpanKind.Client)
    .addAttributes(commonAttributes)
    .addAttribute(InternalMessagingAttributes.Send)
    .build

  def pusher: Resource[F, QueuePusher[F, T]] =
    underlying.pusher.map(new MeasuringQueuePusher(_, new QueueMetrics[F](queueName, requestCounter), pushSpanOps))

}
