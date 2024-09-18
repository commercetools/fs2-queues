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
import com.commercetools.queue.{QueuePusher, UnsealedQueuePusher}
import org.typelevel.otel4s.trace.Tracer

import scala.concurrent.duration.FiniteDuration

private class MeasuringQueuePusher[F[_], T](
  underlying: QueuePusher[F, T],
  metrics: QueueMetrics[F],
  tracer: Tracer[F]
)(implicit F: MonadCancel[F, Throwable])
  extends UnsealedQueuePusher[F, T] {

  override def queueName: String = underlying.queueName

  override def push(message: T, metadata: Map[String, String], delay: Option[FiniteDuration]): F[Unit] =
    tracer
      .span("queue.pushMessage")
      .surround {
        underlying
          .push(message, metadata, delay)
      }
      .guaranteeCase(metrics.send)

  override def push(messages: List[(T, Map[String, String])], delay: Option[FiniteDuration]): F[Unit] =
    tracer
      .span("queue.pushMessages")
      .surround {
        underlying
          .push(messages, delay)
      }
      .guaranteeCase(metrics.send)

}
