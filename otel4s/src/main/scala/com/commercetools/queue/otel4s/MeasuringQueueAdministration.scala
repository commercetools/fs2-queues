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
import com.commercetools.queue.{QueueAdministration, QueueConfiguration, QueueCreationConfiguration}
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Counter
import org.typelevel.otel4s.trace.Tracer

import scala.concurrent.duration.FiniteDuration

class MeasuringQueueAdministration[F[_]](
  underlying: QueueAdministration[F],
  requestCounter: Counter[F, Long],
  tracer: Tracer[F]
)(implicit F: MonadCancel[F, Throwable])
  extends QueueAdministration[F] {

  override def create(name: String, configuration: QueueCreationConfiguration): F[Unit] =
    tracer
      .span("queue.create")
      .surround {
        underlying.create(name, configuration)
      }
      .guaranteeCase(QueueMetrics.increment(Attribute("queue", name), QueueMetrics.create, requestCounter))

  override def update(name: String, messageTTL: Option[FiniteDuration], lockTTL: Option[FiniteDuration]): F[Unit] =
    tracer
      .span("queue.update")
      .surround {
        underlying.update(name, messageTTL, lockTTL)
      }
      .guaranteeCase(QueueMetrics.increment(Attribute("queue", name), QueueMetrics.update, requestCounter))

  override def configuration(name: String): F[QueueConfiguration] =
    tracer
      .span("queue.configuration")
      .surround {
        underlying.configuration(name)
      }
      .guaranteeCase(QueueMetrics.increment(Attribute("queue", name), QueueMetrics.configuration, requestCounter))

  override def delete(name: String): F[Unit] =
    tracer
      .span("queue.delete")
      .surround {
        underlying.delete(name)
      }
      .guaranteeCase(QueueMetrics.increment(Attribute("queue", name), QueueMetrics.delete, requestCounter))

  override def exists(name: String): F[Boolean] =
    tracer
      .span("queue.exists")
      .surround {
        underlying.exists(name)
      }
      .guaranteeCase(QueueMetrics.increment(Attribute("queue", name), QueueMetrics.exist, requestCounter))
}
