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
import com.commercetools.queue.{MessageContext, UnsealedQueuePuller}
import fs2.Chunk

import scala.concurrent.duration.FiniteDuration

class TestQueuePuller[T](queue: TestQueue[T]) extends UnsealedQueuePuller[IO, T] {

  override val queueName: String = queue.name

  override def pullBatch(batchSize: Int, waitingTime: FiniteDuration): IO[Chunk[MessageContext[IO, T]]] =
    IO.sleep(waitingTime) *> queue.lockMessages(batchSize)

}
