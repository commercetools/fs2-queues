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

import cats.effect.{Resource, Temporal}
import cats.syntax.all._
import com.commercetools.queue.{Decision, Message, MessageHandler, QueuePublisher, QueuePuller, QueueSubscriber, UnsealedQueueSubscriber}
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.semconv.attributes.ErrorAttributes
import org.typelevel.otel4s.trace.{SpanKind, StatusCode, Tracer}

import scala.concurrent.duration.FiniteDuration

private class MeasuringQueueSubscriber[F[_], T](
  underlying: QueueSubscriber[F, T],
  metrics: QueueMetrics[F],
  tracer: Tracer[F],
  commonAttributes: Attributes
)(implicit F: Temporal[F])
  extends UnsealedQueueSubscriber[F, T] {

  override def queueName: String = underlying.queueName

  // used by all pullers from this subscriber
  private val pullSpanOps = tracer
    .spanBuilder(s"receive $queueName")
    .withSpanKind(SpanKind.Client)
    .addAttributes(commonAttributes)
    .addAttribute(InternalMessagingAttributes.Receive)
    .build

  // used for all messages pulled from this queue by pullers from this subscriber
  private val settleSpanOps = tracer
    .spanBuilder(s"settle $queueName")
    .withSpanKind(SpanKind.Client)
    .addAttributes(commonAttributes)
    .addAttribute(InternalMessagingAttributes.BatchSingleton)
    .addAttribute(InternalMessagingAttributes.Settle)
    .build

  // used for all message batches pulled from this queue by pullers from this subscriber
  private val settleBatchSpanBuilder = tracer
    .spanBuilder(s"settle $queueName")
    .withSpanKind(SpanKind.Client)
    .addAttributes(commonAttributes)
    .addAttribute(InternalMessagingAttributes.Settle)

  // used by the automated processing streams below
  private val processSpanOps =
    tracer
      .spanBuilder(s"process $queueName")
      .withSpanKind(SpanKind.Consumer)
      .addAttributes(commonAttributes)
      .addAttribute(InternalMessagingAttributes.Process)
      .build

  override def puller: Resource[F, QueuePuller[F, T]] =
    underlying.puller.map(new MeasuringQueuePuller(_, metrics, pullSpanOps, settleSpanOps, settleBatchSpanBuilder))

  override def processWithAutoAck[Res](batchSize: Int, waitingTime: FiniteDuration)(f: Message[F, T] => F[Res])
    : fs2.Stream[F, Res] =
    super.processWithAutoAck(batchSize, waitingTime) { msg =>
      metrics.process.surround {
        processSpanOps.surround {
          f(msg)
        }
      }
    }

  override def attemptProcessWithAutoAck[Res](batchSize: Int, waitingTime: FiniteDuration)(f: Message[F, T] => F[Res])
    : fs2.Stream[F, Either[Throwable, Res]] =
    super.attemptProcessWithAutoAck(batchSize, waitingTime) { msg =>
      metrics.process.surround {
        processSpanOps.surround {
          f(msg)
        }
      }
    }

  override def process[Res](
    batchSize: Int,
    waitingTime: FiniteDuration,
    publisherForReenqueue: QueuePublisher[F, T]
  )(handler: MessageHandler[F, T, Res, Decision]
  ): fs2.Stream[F, Either[Throwable, Res]] =
    super.process(batchSize, waitingTime, publisherForReenqueue) { msg =>
      metrics.process.surround {
        processSpanOps.use { span =>
          handler.handle(msg).flatTap {
            case Decision.Fail(t, _) =>
              for {
                _ <- span.setStatus(StatusCode.Error)
                _ <- span.addAttribute(ErrorAttributes.ErrorType(t.getClass().getName()))
                _ <- span.recordException(t)
              } yield ()
            case _ =>
              F.unit
          }
        }
      }
    }

}
