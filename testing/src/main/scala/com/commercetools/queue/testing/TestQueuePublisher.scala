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
import com.commercetools.queue.{QueuePublisher, QueuePusher, UnsealedQueuePublisher}

import scala.concurrent.duration.FiniteDuration

final private class TestQueuePublisher[T](queue: TestQueue[T]) extends UnsealedQueuePublisher[IO, T] {

  override val queueName = queue.name

  override def pusher: Resource[IO, QueuePusher[IO, T]] = Resource.pure(new TestQueuePusher(queue))

}

/**
 * Utilities to create [[QueuePublisher]]s for testing purpose.
 */
object TestQueuePublisher {

  /** Creates a test publisher based on the provided test queue. */
  def apply[T](queue: TestQueue[T]): QueuePublisher[IO, T] = new TestQueuePublisher[T](queue)

  /** Creates a testing publisher based on a function to execute for each pushed message. */
  def fromPusher[T](onPush: (T, Map[String, String], Option[FiniteDuration]) => IO[Unit]): QueuePublisher[IO, T] =
    new UnsealedQueuePublisher[IO, T] {

      override def queueName: String = "mock-queue"

      override def pusher: Resource[IO, QueuePusher[IO, T]] = Resource.pure(TestQueuePusher.fromPush(onPush))

    }

}
