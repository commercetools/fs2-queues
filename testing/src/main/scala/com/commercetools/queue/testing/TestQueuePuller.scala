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

package com.commercetools.queue.testing

import cats.effect.IO
import cats.syntax.foldable._
import com.commercetools.queue.{Message, MessageBatch, MessageContext, MessageId, QueuePuller, UnsealedMessageBatch, UnsealedQueuePuller}
import fs2.Chunk

import scala.concurrent.duration.FiniteDuration

/**
 * A queue puller for testing purpose only.
 *
 * It always wait for the provided waiting time before returning messages, even if the messages are already available.
 */
final private class TestQueuePuller[T](queue: TestQueue[T]) extends UnsealedQueuePuller[IO, T] {

  override val queueName: String = queue.name

  override def pullBatch(batchSize: Int, waitingTime: FiniteDuration): IO[Chunk[MessageContext[IO, T]]] =
    IO.sleep(waitingTime) *> queue.lockMessages(batchSize)

  override def pullMessageBatch(batchSize: Int, waitingTime: FiniteDuration): IO[MessageBatch[IO, T]] =
    pullBatch(batchSize, waitingTime).map { batch =>
      new UnsealedMessageBatch[IO, T] {
        override def messages: Chunk[Message[IO, T]] = batch
        override def ackAll: IO[List[MessageId]] = batch.traverse_(_.ack()).map(_ => List())
        override def nackAll: IO[List[MessageId]] = batch.traverse_(_.nack()).map(_ => List())
      }
    }
}

/**
 * Utilities to create [[QueuePuller]]s for testing purpose.
 */
object TestQueuePuller {

  /** Creates a test puller based on the provided test queue. */
  def apply[T](queue: TestQueue[T]): QueuePuller[IO, T] =
    new TestQueuePuller[T](queue)

  /** Creates a testing puller based on a function to execute for each pull. */
  def fromPull[T](onPull: (Int, FiniteDuration) => IO[Chunk[MessageContext[IO, T]]]): QueuePuller[IO, T] =
    new UnsealedQueuePuller[IO, T] {

      override def queueName: String = "mock-queue"

      override def pullBatch(batchSize: Int, waitingTime: FiniteDuration): IO[Chunk[MessageContext[IO, T]]] =
        onPull(batchSize, waitingTime)

      override def pullMessageBatch(batchSize: Int, waitingTime: FiniteDuration): IO[MessageBatch[IO, T]] =
        pullBatch(batchSize, waitingTime).map { batch =>
          new UnsealedMessageBatch[IO, T] {
            override def messages: Chunk[Message[IO, T]] = batch
            override def ackAll: IO[List[MessageId]] = batch.traverse_(_.ack()).map(_ => List())
            override def nackAll: IO[List[MessageId]] = batch.traverse_(_.nack()).map(_ => List())
          }
        }
    }

}
