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

import cats.data.Chain
import cats.effect.IO
import com.commercetools.queue.QueuePusher
import munit.CatsEffectSuite
import org.typelevel.otel4s.trace.Tracer

import scala.concurrent.duration.FiniteDuration

class MeasuringPusherSuite extends CatsEffectSuite {

  def pusher(result: IO[Unit]) = new QueuePusher[IO, String] {

    override def push(message: String, delay: Option[FiniteDuration]): IO[Unit] = result

    override def push(messages: List[String], delay: Option[FiniteDuration]): IO[Unit] = result

  }

  test("Successfully pushing one message results in incrementing the counter") {
    NaiveCounter.create.flatMap { counter =>
      val measuringPusher = new MeasuringQueuePusher[IO, String](pusher(IO.unit), counter, Tracer.noop)
      for {
        fiber <- measuringPusher.push("msg", None).start
        _ <- assertIO(fiber.join.map(_.isSuccess), true)
        _ <- assertIO(counter.records.get, Chain.one((1L, List(Attributes.send, Attributes.success))))
      } yield ()
    }
  }

  test("Successfully pushing several messages results in incrementing the counter") {
    NaiveCounter.create.flatMap { counter =>
      val measuringPusher = new MeasuringQueuePusher[IO, String](pusher(IO.unit), counter, Tracer.noop)
      for {
        fiber <- measuringPusher.push(List("msg1", "msg2", "msg3"), None).start
        _ <- assertIO(fiber.join.map(_.isSuccess), true)
        _ <- assertIO(counter.records.get, Chain.one((1L, List(Attributes.send, Attributes.success))))
      } yield ()
    }
  }

  test("Failing to push one message results in incrementing the counter") {
    NaiveCounter.create.flatMap { counter =>
      val measuringPusher =
        new MeasuringQueuePusher[IO, String](pusher(IO.raiseError(new Exception)), counter, Tracer.noop)
      for {
        fiber <- measuringPusher.push("msg", None).start
        _ <- assertIO(fiber.join.map(_.isError), true)
        _ <- assertIO(counter.records.get, Chain.one((1L, List(Attributes.send, Attributes.failure))))
      } yield ()
    }
  }

  test("Failing to push several messages results in incrementing the counter") {
    NaiveCounter.create.flatMap { counter =>
      val measuringPusher =
        new MeasuringQueuePusher[IO, String](pusher(IO.raiseError(new Exception)), counter, Tracer.noop)
      for {
        fiber <- measuringPusher.push(List("msg1", "msg2", "msg3"), None).start
        _ <- assertIO(fiber.join.map(_.isError), true)
        _ <- assertIO(counter.records.get, Chain.one((1L, List(Attributes.send, Attributes.failure))))
      } yield ()
    }
  }

  test("Canceling pushing one message results in incrementing the counter") {
    NaiveCounter.create.flatMap { counter =>
      val measuringPusher = new MeasuringQueuePusher[IO, String](pusher(IO.canceled), counter, Tracer.noop)
      for {
        fiber <- measuringPusher.push("msg", None).start
        _ <- assertIO(fiber.join.map(_.isCanceled), true)
        _ <- assertIO(counter.records.get, Chain.one((1L, List(Attributes.send, Attributes.cancelation))))
      } yield ()
    }
  }

  test("Canceling pushing several messages results in incrementing the counter") {
    NaiveCounter.create.flatMap { counter =>
      val measuringPusher = new MeasuringQueuePusher[IO, String](pusher(IO.canceled), counter, Tracer.noop)
      for {
        fiber <- measuringPusher.push(List("msg1", "msg2", "msg3"), None).start
        _ <- assertIO(fiber.join.map(_.isCanceled), true)
        _ <- assertIO(counter.records.get, Chain.one((1L, List(Attributes.send, Attributes.cancelation))))
      } yield ()
    }
  }

}
