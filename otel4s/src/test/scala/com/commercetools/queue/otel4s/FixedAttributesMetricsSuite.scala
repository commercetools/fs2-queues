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

import cats.effect.IO
import com.commercetools.queue.testing.{TestQueuePublisher, TestQueueSubscriber, TestingMessageContext}
import fs2.Chunk
import munit.CatsEffectSuite
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.semconv.attributes.ErrorAttributes
import org.typelevel.otel4s.semconv.experimental.attributes.MessagingExperimentalAttributes
import org.typelevel.otel4s.trace.Tracer

import scala.concurrent.duration._

class FixedAttributesMetricsSuite extends CatsEffectSuite with TestMetrics {

  val queueName = "test-queue"

  private val threeStateEffect =
    IO.ref(0).map { counter =>
      for {
        call <- counter.getAndUpdate(_ + 1)
        _ <-
          if (call % 3 == 0) {
            IO.unit
          } else if (call % 3 == 1) {
            IO.raiseError(new Exception)
          } else {
            IO.canceled
          }
      } yield ()
    }

  private def queueSubscriber(metrics: QueueMetrics[IO]) =
    threeStateEffect.map { effect =>
      new MeasuringQueueSubscriber[IO, String](
        TestQueueSubscriber.fromPuller((_, _) => effect.as(Chunk.singleton(TestingMessageContext("msg").noop))),
        metrics,
        Tracer.noop,
        Attributes.empty
      )
    }

  private def queuePublisher(metrics: QueueMetrics[IO]) =
    threeStateEffect.map { effect =>
      new MeasuringQueuePublisher[IO, String](
        TestQueuePublisher.fromPusher((_, _, _) => effect),
        metrics,
        Tracer.noop,
        Attributes.empty
      )
    }

  private def messageContext(metrics: QueueMetrics[IO]) =
    threeStateEffect.map { effect =>
      new MeasuringMessageContext[IO, String](
        TestingMessageContext("msg").forEffects(effect, effect, effect),
        metrics,
        Tracer.noop[IO].span(""))
    }

  test("Operation duration metrics should always have the same label set") {
    testkitMetrics(true).use { case (testkit, metrics) =>
      for {
        // pull 3 times success/error/cancel
        subscriber <- queueSubscriber(metrics)
        fiber <- subscriber.puller.use(_.pullBatch(1, 1.second).attempt.replicateA_(3)).start
        _ <- fiber.join
        // push 3 times success/error/cancel
        publisher <- queuePublisher(metrics)
        fiber <- publisher.pusher.use(_.push("", Map.empty, None).attempt.replicateA_(3)).start
        _ <- fiber.join
        ctx <- messageContext(metrics)
        // ack 3 times success/error/cancel
        fiber <- ctx.ack().attempt.replicateA_(3).start
        _ <- fiber.join
        // nack 3 times success/error/cancel
        fiber <- ctx.nack().attempt.replicateA_(3).start
        _ <- fiber.join
        // extendLock 3 times success/error/cancel
        fiber <- ctx.extendLock().attempt.replicateA_(3).start
        _ <- fiber.join
        // collect metrics data
        data <- testkit.collectMetrics
      } yield {
        val operations =
          data.filter(_.name == QueueMetrics.OperationDurationHistogramName).flatMap(_.data.points.toVector)
        assertEquals(operations.size, 15)

        operations.map(_.attributes.map(_.key.name).toList.sorted).foreach { attributesNames =>
          // all recording have the 3 attributes set
          assertEquals(attributesNames.size, 3)
          assertEquals(
            attributesNames,
            List(
              ErrorAttributes.ErrorType.name,
              MessagingExperimentalAttributes.MessagingOperationName.name,
              MessagingExperimentalAttributes.MessagingOperationType.name
            )
          )
        }
      }
    }
  }

  test("Operation duration metrics should not have the same label set by default") {
    testkitMetrics(false).use { case (testkit, metrics) =>
      for {
        // pull 3 times success/error/cancel
        subscriber <- queueSubscriber(metrics)
        fiber <- subscriber.puller.use(_.pullBatch(1, 1.second).attempt.replicateA_(3)).start
        _ <- fiber.join
        // push 3 times success/error/cancel
        publisher <- queuePublisher(metrics)
        fiber <- publisher.pusher.use(_.push("", Map.empty, None).attempt.replicateA_(3)).start
        _ <- fiber.join
        ctx <- messageContext(metrics)
        // ack 3 times success/error/cancel
        fiber <- ctx.ack().attempt.replicateA_(3).start
        _ <- fiber.join
        // nack 3 times success/error/cancel
        fiber <- ctx.nack().attempt.replicateA_(3).start
        _ <- fiber.join
        // extendLock 3 times success/error/cancel
        fiber <- ctx.extendLock().attempt.replicateA_(3).start
        _ <- fiber.join
        // collect metrics data
        data <- testkit.collectMetrics
      } yield {
        val operations =
          data.filter(_.name == QueueMetrics.OperationDurationHistogramName).flatMap(_.data.points.toVector)
        assertEquals(operations.size, 15)

        val (ok, errored) = operations.partition(_.attributes.size == 2)
        assertEquals(ok.size, 5)
        ok.map(_.attributes.map(_.key.name).toList.sorted).foreach { attributesNames =>
          // all recording have the 3 attributes set
          assertEquals(attributesNames.size, 2)
          assertEquals(
            attributesNames,
            List(
              MessagingExperimentalAttributes.MessagingOperationName.name,
              MessagingExperimentalAttributes.MessagingOperationType.name
            )
          )
        }
        assertEquals(errored.size, 10)
        errored.map(_.attributes.map(_.key.name).toList.sorted).foreach { attributesNames =>
          // all recording have the 3 attributes set
          assertEquals(attributesNames.size, 3)
          assertEquals(
            attributesNames,
            List(
              ErrorAttributes.ErrorType.name,
              MessagingExperimentalAttributes.MessagingOperationName.name,
              MessagingExperimentalAttributes.MessagingOperationType.name
            )
          )
        }
      }
    }
  }

}
