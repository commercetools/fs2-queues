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

import cats.effect.Outcome
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Counter

private class QueueMetrics[F[_]](queueName: String, requestCounter: Counter[F, Long]) {
  final private[this] val queue = Attribute("queue", queueName)

  final val send: Outcome[F, Throwable, _] => F[Unit] = QueueMetrics.increment(queue, QueueMetrics.send, requestCounter)
  final val receive: Outcome[F, Throwable, _] => F[Unit] =
    QueueMetrics.increment(queue, QueueMetrics.receive, requestCounter)
  final val ack: Outcome[F, Throwable, _] => F[Unit] = QueueMetrics.increment(queue, QueueMetrics.ack, requestCounter)
  final val nack: Outcome[F, Throwable, _] => F[Unit] = QueueMetrics.increment(queue, QueueMetrics.nack, requestCounter)
  final val extendLock: Outcome[F, Throwable, _] => F[Unit] =
    QueueMetrics.increment(queue, QueueMetrics.extendLock, requestCounter)

}

private object QueueMetrics {

  // queue instance attributes
  final val send = Attribute("method", "send")
  final val receive = Attribute("method", "receive")
  final val ack = Attribute("method", "ack")
  final val nack = Attribute("method", "nack")
  final val extendLock = Attribute("method", "extendLock")

  // queue management attributes
  final val create = Attribute("method", "create")
  final val update = Attribute("method", "update")
  final val configuration = Attribute("method", "configuration")
  final val delete = Attribute("method", "delete")
  final val exist = Attribute("method", "exist")

  final val success = Attribute("outcome", "success")
  final val failure = Attribute("outcome", "failure")
  final val cancelation = Attribute("outcome", "cancelation")

  def increment[F[_]](
    queue: Attribute[String],
    method: Attribute[String],
    counter: Counter[F, Long]
  ): Outcome[F, Throwable, _] => F[Unit] = {
    case Outcome.Succeeded(_) =>
      counter.inc(queue, method, success)
    case Outcome.Errored(_) =>
      counter.inc(queue, method, failure)
    case Outcome.Canceled() =>
      counter.inc(queue, method, cancelation)
  }

}
