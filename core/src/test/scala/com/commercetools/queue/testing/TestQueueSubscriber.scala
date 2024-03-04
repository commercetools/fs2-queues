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
import com.commercetools.queue.{MessageContext, QueueSubscriber}
import fs2.Stream

import scala.concurrent.duration.FiniteDuration

class TestQueueSubscriber[T](queue: TestQueue[T]) extends QueueSubscriber[IO, T] {

  override def messages(batchSize: Int, waitingTime: FiniteDuration): fs2.Stream[IO, MessageContext[IO, T]] =
    (Stream.sleep_[IO](waitingTime) ++
      Stream
        .eval(queue.lockMessages(batchSize))
        .unchunks).repeat

}
