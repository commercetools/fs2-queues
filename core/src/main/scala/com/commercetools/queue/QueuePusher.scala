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

package com.commercetools.queue

import scala.concurrent.duration.FiniteDuration

/**
 * A queue puller allows for pushing elements into a queue either on at a time
 * or in batch.
 */
trait QueuePusher[F[_], T] {

  /**
   * Publishes a single message to the queue, with an optional delay.
   */
  def push(message: T, delay: Option[FiniteDuration]): F[Unit]

  /**
   * Publishes a bunch of messages to the queue, with an optional delay.
   */
  def push(messages: List[T], delay: Option[FiniteDuration]): F[Unit]

}
