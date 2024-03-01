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

import fs2.Pipe

import scala.concurrent.duration.FiniteDuration

/**
 * The interface to publish to a queue.
 */
trait QueuePublisher[F[_], T] {

  /**
   * Publishes a single message to the queue, with an optional delay.
   */
  def publish(message: T, delay: Option[FiniteDuration]): F[Unit]

  /**
   * Publishes a bunch of messages to the queue, with an optional delay.
   */
  def publish(messages: List[T], delay: Option[FiniteDuration]): F[Unit]

  /**
   * Sink to pipe your [[fs2.Stream Stream]] into, in order to publish
   * produced data to the queue. The messages are published in batches, according
   * to the `batchSize` parameter.
   */
  def sink(batchSize: Int = 10): Pipe[F, T, Nothing] =
    _.chunkN(batchSize).foreach { chunk =>
      publish(chunk.toList, None)
    }

}
