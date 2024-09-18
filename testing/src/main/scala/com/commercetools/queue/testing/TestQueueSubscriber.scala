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

import cats.effect.{IO, Resource}
import com.commercetools.queue.{MessageContext, QueuePuller, QueueSubscriber, UnsealedQueueSubscriber}
import fs2.Chunk

import scala.concurrent.duration.FiniteDuration

/**
 * A queue subscriber for testing purpose only.
 */
final private class TestQueueSubscriber[T](queue: TestQueue[T]) extends UnsealedQueueSubscriber[IO, T] {

  override val queueName: String = queue.name

  override def puller: Resource[IO, QueuePuller[IO, T]] = Resource.pure(new TestQueuePuller(queue))

}

/**
 * Utilities to create [[QueueSubscriber]]s for testing purpose.
 */
object TestQueueSubscriber {

  /** Creates a test subscriber based on the provided test queue. */
  def apply[T](queue: TestQueue[T]): QueueSubscriber[IO, T] =
    new TestQueueSubscriber[T](queue)

  /** Creates a testing subscriber based on a function to execute for each pull. */
  def fromPuller[T](onPull: (Int, FiniteDuration) => IO[Chunk[MessageContext[IO, T]]]): QueueSubscriber[IO, T] =
    new UnsealedQueueSubscriber[IO, T] {

      override def queueName: String = "mock-queue"

      override def puller: Resource[IO, QueuePuller[IO, T]] =
        Resource.pure(TestQueuePuller.fromPull(onPull))

    }

}
