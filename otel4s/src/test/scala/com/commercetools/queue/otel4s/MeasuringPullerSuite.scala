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
import com.commercetools.queue.testing.TestingMessageContext
import com.commercetools.queue.{MessageContext, QueuePuller}
import fs2.Chunk
import munit.CatsEffectSuite
import org.typelevel.otel4s.trace.Tracer

import scala.concurrent.duration.{Duration, FiniteDuration}

class MeasuringPullerSuite extends CatsEffectSuite {
  def puller(batch: IO[Chunk[MessageContext[IO, String]]]) = new QueuePuller[IO, String] {
    override def pullBatch(batchSize: Int, waitingTime: FiniteDuration): IO[Chunk[MessageContext[IO, String]]] =
      batch
  }

  test("Successful pulling results in incrementing the counter") {
    NaiveCounter.create.flatMap { counter =>
      val measuringPuller = new MeasuringQueuePuller[IO, String](
        puller(
          IO.pure(
            Chunk.from(
              List(
                TestingMessageContext("first").noop,
                TestingMessageContext("second").noop,
                TestingMessageContext("third").noop,
                TestingMessageContext("forth").noop)))),
        counter,
        Tracer.noop
      )
      for {
        fiber <- measuringPuller.pullBatch(0, Duration.Zero).start
        _ <- assertIO(fiber.join.map(_.isSuccess), true)
        _ <- assertIO(counter.records.get, Chain.one((1L, List(Attributes.receive, Attributes.success))))
      } yield ()
    }
  }

  test("Failed pulling results in incrementing the counter") {
    NaiveCounter.create.flatMap { counter =>
      val measuringPuller =
        new MeasuringQueuePuller[IO, String](puller(IO.raiseError(new Exception)), counter, Tracer.noop)
      for {
        fiber <- measuringPuller.pullBatch(0, Duration.Zero).start
        _ <- assertIO(fiber.join.map(_.isError), true)
        _ <- assertIO(counter.records.get, Chain.one((1L, List(Attributes.receive, Attributes.failure))))
      } yield ()
    }
  }

  test("Cancelled pulling results in incrementing the counter") {
    NaiveCounter.create.flatMap { counter =>
      val measuringPuller =
        new MeasuringQueuePuller[IO, String](puller(IO.canceled.as(Chunk.empty)), counter, Tracer.noop)
      for {
        fiber <- measuringPuller.pullBatch(0, Duration.Zero).start
        _ <- assertIO(fiber.join.map(_.isCanceled), true)
        _ <- assertIO(counter.records.get, Chain.one((1L, List(Attributes.receive, Attributes.cancelation))))
      } yield ()
    }
  }

}
