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
import com.commercetools.queue.{QueuePusher, UnsealedQueuePusher}

import scala.concurrent.duration.FiniteDuration

final private class TestQueuePusher[T](queue: TestQueue[T]) extends UnsealedQueuePusher[IO, T] {

  override val queueName: String = queue.name

  override def push(message: T, metadata: Map[String, String], delay: Option[FiniteDuration]): IO[Unit] =
    queue.enqeueMessages((message, metadata) :: Nil, delay)

  override def push(messages: List[(T, Map[String, String])], delay: Option[FiniteDuration]): IO[Unit] =
    queue.enqeueMessages(messages, delay)

}

/**
 * Utilities to create [[QueuePusher]]s for testing purpose.
 */
object TestQueuePusher {

  /** Creates a test pusher based on the provided test queue. */
  def apply[T](queue: TestQueue[T]): QueuePusher[IO, T] = new TestQueuePusher[T](queue)

  /** Creates a testing pusher based on a function to execute for each pushed message. */
  def fromPush[T](onPush: (T, Map[String, String], Option[FiniteDuration]) => IO[Unit]): QueuePusher[IO, T] =
    new UnsealedQueuePusher[IO, T] {

      override def queueName: String = "mock-queue"

      override def push(message: T, metadata: Map[String, String], delay: Option[FiniteDuration]): IO[Unit] =
        onPush(message, metadata, delay)

      override def push(messages: List[(T, Map[String, String])], delay: Option[FiniteDuration]): IO[Unit] =
        messages.traverse_ { case (message, metadata) =>
          onPush(message, metadata, delay)
        }

    }

}
