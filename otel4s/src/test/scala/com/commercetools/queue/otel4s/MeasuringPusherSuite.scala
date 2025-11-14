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
import com.commercetools.queue.QueuePusher
import com.commercetools.queue.testing.TestQueuePusher
import munit.CatsEffectSuite
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.semconv.attributes.ErrorAttributes
import org.typelevel.otel4s.semconv.experimental.attributes.MessagingExperimentalAttributes
import org.typelevel.otel4s.trace.Tracer

class MeasuringPusherSuite extends CatsEffectSuite with TestMetrics {
  self =>

  val queueName = "test-queue"

  val queueAttribute = MessagingExperimentalAttributes.MessagingDestinationName(queueName)

  val spanOps = Tracer.noop[IO].span("")

  def pusher(result: IO[Unit]): QueuePusher[IO, String] =
    TestQueuePusher.fromPush[String]((_, _, _) => result)

  test("Successfully pushing one message results in incrementing the counter") {
    testkitMetrics.use { case (testkit, metrics) =>
      val measuringPusher =
        new MeasuringQueuePusher[IO, String](pusher(IO.unit), metrics.forQueue(queueName), spanOps)
      for {
        fiber <- measuringPusher.push("msg", Map.empty, None).start
        _ <- assertIO(fiber.join.map(_.isSuccess), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              QueueMetrics.SentMessagesCounterName,
              Vector(CounterDataPoint(1L, Attributes(queueAttribute, InternalMessagingAttributes.Send)))))
        )
      } yield ()
    }
  }

  test("Successfully pushing several messages results in incrementing the counter") {
    testkitMetrics.use { case (testkit, metrics) =>
      val measuringPusher =
        new MeasuringQueuePusher[IO, String](pusher(IO.unit), metrics.forQueue(queueName), spanOps)
      for {
        fiber <- measuringPusher.push(List("msg1", "msg2", "msg3").map(x => (x, Map.empty)), None).start
        _ <- assertIO(fiber.join.map(_.isSuccess), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              QueueMetrics.SentMessagesCounterName,
              Vector(CounterDataPoint(3L, Attributes(queueAttribute, InternalMessagingAttributes.Send)))))
        )
      } yield ()
    }
  }

  test("Failing to push one message results in incrementing the counter") {
    testkitMetrics.use { case (testkit, metrics) =>
      val measuringPusher =
        new MeasuringQueuePusher[IO, String](pusher(IO.raiseError(new Exception)), metrics.forQueue(queueName), spanOps)
      for {
        fiber <- measuringPusher.push("msg", Map.empty, None).start
        _ <- assertIO(fiber.join.map(_.isError), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              QueueMetrics.SentMessagesCounterName,
              Vector(
                CounterDataPoint(
                  1L,
                  Attributes(
                    queueAttribute,
                    InternalMessagingAttributes.Send,
                    ErrorAttributes.ErrorType("java.lang.Exception"))))
            ))
        )
      } yield ()
    }
  }

  test("Failing to push several messages results in incrementing the counter") {
    testkitMetrics.use { case (testkit, metrics) =>
      val measuringPusher =
        new MeasuringQueuePusher[IO, String](pusher(IO.raiseError(new Exception)), metrics.forQueue(queueName), spanOps)
      for {
        fiber <- measuringPusher.push(List("msg1", "msg2", "msg3").map(x => (x, Map.empty)), None).start
        _ <- assertIO(fiber.join.map(_.isError), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              QueueMetrics.SentMessagesCounterName,
              Vector(
                CounterDataPoint(
                  3L,
                  Attributes(
                    queueAttribute,
                    InternalMessagingAttributes.Send,
                    ErrorAttributes.ErrorType("java.lang.Exception"))))
            ))
        )
      } yield ()
    }
  }

}
