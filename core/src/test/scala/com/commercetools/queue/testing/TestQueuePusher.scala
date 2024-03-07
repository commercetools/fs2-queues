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
import com.commercetools.queue.QueuePusher

import scala.concurrent.duration.FiniteDuration

class TestQueuePusher[T](queue: TestQueue[T]) extends QueuePusher[IO, T] {

  override def push(message: T, delay: Option[FiniteDuration]): IO[Unit] =
    queue.enqeueMessages(message :: Nil, delay)

  override def push(messages: List[T], delay: Option[FiniteDuration]): IO[Unit] =
    queue.enqeueMessages(messages, delay)

}
