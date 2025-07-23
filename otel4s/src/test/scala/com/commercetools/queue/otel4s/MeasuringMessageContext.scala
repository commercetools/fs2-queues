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
import com.commercetools.queue.testing.TestingMessageContext
import munit.CatsEffectSuite
import org.typelevel.otel4s.trace.Tracer
import org.typelevel.otel4s.{Attribute, Attributes}

class MeasuringMessageContextSuite extends CatsEffectSuite with TestMetrics {

  val queueName = "test-queue"

  val queueAttribute = Attribute("queue", queueName)

  test("Succesfully acking a message should increment the request counter") {
    testkitCounter("ack-counter").use { case (testkit, counter) =>
      val context = new MeasuringMessageContext[IO, String](
        TestingMessageContext("").noop,
        new QueueMetrics(queueName, counter),
        Tracer.noop)
      for {
        fiber <- context.ack().start
        _ <- assertIO(fiber.join.map(_.isSuccess), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              "ack-counter",
              Vector(CounterDataPoint(1L, Attributes(queueAttribute, QueueMetrics.ack, QueueMetrics.success)))))
        )
      } yield ()
    }
  }

  test("Failing to ack a message should increment the request counter") {
    testkitCounter("ack-counter").use { case (testkit, counter) =>
      val context =
        new MeasuringMessageContext[IO, String](
          TestingMessageContext("").failing(new Exception),
          new QueueMetrics(queueName, counter),
          Tracer.noop)
      for {
        fiber <- context.ack().start
        _ <- assertIO(fiber.join.map(_.isError), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              "ack-counter",
              Vector(CounterDataPoint(1L, Attributes(queueAttribute, QueueMetrics.ack, QueueMetrics.failure)))))
        )
      } yield ()
    }
  }

  test("Cancelling acking a message should increment the request counter") {
    testkitCounter("ack-counter").use { case (testkit, counter) =>
      val context = new MeasuringMessageContext[IO, String](
        TestingMessageContext("").canceled,
        new QueueMetrics(queueName, counter),
        Tracer.noop)
      for {
        fiber <- context.ack().start
        _ <- assertIO(fiber.join.map(_.isCanceled), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              "ack-counter",
              Vector(CounterDataPoint(1L, Attributes(queueAttribute, QueueMetrics.ack, QueueMetrics.cancelation)))))
        )
      } yield ()
    }
  }

  test("Succesfully nacking a message should increment the request counter") {
    testkitCounter("nack-counter").use { case (testkit, counter) =>
      val context = new MeasuringMessageContext[IO, String](
        TestingMessageContext("").noop,
        new QueueMetrics(queueName, counter),
        Tracer.noop)
      for {
        fiber <- context.nack().start
        _ <- assertIO(fiber.join.map(_.isSuccess), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              "nack-counter",
              Vector(CounterDataPoint(1L, Attributes(queueAttribute, QueueMetrics.nack, QueueMetrics.success)))))
        )
      } yield ()
    }
  }

  test("Failing to nack a message should increment the request counter") {
    testkitCounter("nack-counter").use { case (testkit, counter) =>
      val context =
        new MeasuringMessageContext[IO, String](
          TestingMessageContext("").failing(new Exception),
          new QueueMetrics(queueName, counter),
          Tracer.noop)
      for {
        fiber <- context.nack().start
        _ <- assertIO(fiber.join.map(_.isError), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              "nack-counter",
              Vector(CounterDataPoint(1L, Attributes(queueAttribute, QueueMetrics.nack, QueueMetrics.failure)))))
        )
      } yield ()
    }
  }

  test("Cancelling nacking a message should increment the request counter") {
    testkitCounter("nack-counter").use { case (testkit, counter) =>
      val context = new MeasuringMessageContext[IO, String](
        TestingMessageContext("").canceled,
        new QueueMetrics(queueName, counter),
        Tracer.noop)
      for {
        fiber <- context.nack().start
        _ <- assertIO(fiber.join.map(_.isCanceled), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              "nack-counter",
              Vector(CounterDataPoint(1L, Attributes(queueAttribute, QueueMetrics.nack, QueueMetrics.cancelation)))))
        )
      } yield ()
    }
  }

  test("Succesfully extending a message lock should increment the request counter") {
    testkitCounter("extend-counter").use { case (testkit, counter) =>
      val context = new MeasuringMessageContext[IO, String](
        TestingMessageContext("").noop,
        new QueueMetrics(queueName, counter),
        Tracer.noop)
      for {
        fiber <- context.extendLock().start
        _ <- assertIO(fiber.join.map(_.isSuccess), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              "extend-counter",
              Vector(CounterDataPoint(1L, Attributes(queueAttribute, QueueMetrics.extendLock, QueueMetrics.success)))))
        )
      } yield ()
    }
  }

  test("Failing to extend a message lock should increment the request counter") {
    testkitCounter("extend-counter").use { case (testkit, counter) =>
      val context =
        new MeasuringMessageContext[IO, String](
          TestingMessageContext("").failing(new Exception),
          new QueueMetrics(queueName, counter),
          Tracer.noop)
      for {
        fiber <- context.extendLock().start
        _ <- assertIO(fiber.join.map(_.isError), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              "extend-counter",
              Vector(CounterDataPoint(1L, Attributes(queueAttribute, QueueMetrics.extendLock, QueueMetrics.failure)))))
        )
      } yield ()
    }
  }

  test("Cancelling a message extension should increment the request counter") {
    testkitCounter("extend-counter").use { case (testkit, counter) =>
      val context = new MeasuringMessageContext[IO, String](
        TestingMessageContext("").canceled,
        new QueueMetrics(queueName, counter),
        Tracer.noop)
      for {
        fiber <- context.extendLock().start
        _ <- assertIO(fiber.join.map(_.isCanceled), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              "extend-counter",
              Vector(
                CounterDataPoint(1L, Attributes(queueAttribute, QueueMetrics.extendLock, QueueMetrics.cancelation)))))
        )
      } yield ()
    }
  }

}
