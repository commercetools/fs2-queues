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
import cats.syntax.foldable._
import com.commercetools.queue.testing.TestingMessageContext
import com.commercetools.queue.{Message, MessageBatch, MessageContext, MessageId, UnsealedMessageBatch, UnsealedQueuePuller}
import fs2.Chunk
import munit.CatsEffectSuite
import org.typelevel.otel4s.Attributes
import org.typelevel.otel4s.semconv.experimental.attributes.MessagingExperimentalAttributes
import org.typelevel.otel4s.trace.Tracer

import scala.concurrent.duration.{Duration, FiniteDuration}

class MeasuringPullerSuite extends CatsEffectSuite with TestMetrics {
  self =>

  val queueName = "test-queue"

  val queueAttribute = MessagingExperimentalAttributes.MessagingDestinationName(queueName)

  val spanBuilder = Tracer.noop[IO].spanBuilder("")
  val spanOps = spanBuilder.build

  def puller(batch: IO[Chunk[MessageContext[IO, String]]]) = new UnsealedQueuePuller[IO, String] {

    override def queueName: String = self.queueName

    override def pullBatch(batchSize: Int, waitingTime: FiniteDuration): IO[Chunk[MessageContext[IO, String]]] =
      batch

    override def pullMessageBatch(batchSize: Int, waitingTime: FiniteDuration): IO[MessageBatch[IO, String]] =
      pullBatch(batchSize, waitingTime).map { batch =>
        new UnsealedMessageBatch[IO, String] {
          override def messages: Chunk[Message[IO, String]] = batch
          override def ackAll: IO[List[MessageId]] = batch.traverse_(_.ack()).map(_ => List())
          override def nackAll: IO[List[MessageId]] = batch.traverse_(_.nack()).map(_ => List())
        }
      }
  }

  test("Successful pulling results in incrementing the counter") {
    testkitMetrics().use { case (testkit, metrics) =>
      val measuringPuller = new MeasuringQueuePuller[IO, String](
        puller(
          IO.pure(
            Chunk.from(
              List(
                TestingMessageContext("first").noop,
                TestingMessageContext("second").noop,
                TestingMessageContext("third").noop,
                TestingMessageContext("forth").noop)))),
        metrics.forQueue(queueName),
        spanOps,
        spanOps,
        spanBuilder
      )
      for {
        fiber <- measuringPuller.pullBatch(0, Duration.Zero).start
        _ <- assertIO(fiber.join.map(_.isSuccess), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              QueueMetrics.ConsumedMessagesCounterName,
              Vector(CounterDataPoint(4L, Attributes(queueAttribute, InternalMessagingAttributes.Receive)))))
        )
      } yield ()
    }
  }

  test("Successful batch pulling results in incrementing the counter") {
    testkitMetrics().use { case (testkit, metrics) =>
      val measuringPuller = new MeasuringQueuePuller[IO, String](
        puller(
          IO.pure(
            Chunk.from(
              List(
                TestingMessageContext("first").noop,
                TestingMessageContext("second").noop,
                TestingMessageContext("third").noop,
                TestingMessageContext("forth").noop)))),
        metrics.forQueue(queueName),
        spanOps,
        spanOps,
        spanBuilder
      )
      for {
        fiber <- measuringPuller.pullMessageBatch(0, Duration.Zero).start
        _ <- assertIO(fiber.join.map(_.isSuccess), true)
        _ <- assertIO(
          testkit.collectMetrics.map(_.flatMap(CounterData.fromMetricData(_))),
          List(
            CounterData(
              QueueMetrics.ConsumedMessagesCounterName,
              Vector(CounterDataPoint(4L, Attributes(queueAttribute, InternalMessagingAttributes.Receive)))))
        )
      } yield ()
    }
  }

}
