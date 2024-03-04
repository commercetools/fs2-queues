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

import cats.effect.Temporal
import cats.syntax.functor._

import java.time
import scala.concurrent.duration._

/**
 * Interface to interact with a message received from a queue.
 * The messages must be explicitly aknowledged after having been processed.
 */
abstract class MessageContext[F[_], T](implicit F: Temporal[F]) extends Message[T] {

  /**
   * Acknowledges the message. It will be removed from the queue, so that
   * no other subscriber will process it.
   */
  def ack(): F[Unit]

  /**
   * Marks the the message as non acknowledged, for instance in case of an error
   * in processing. The message will be unlocked and available for processing by
   * other subscribers.
   */
  def nack(): F[Unit]

  /**
   * Extends the lock for this message. It gives this processor more time to process
   * it, for instance. The duration by which the lock is extended is either a queue
   * configured duration or on that is configured in the client, depending on the
   * underlying queue system.
   */
  def extendLock(): F[Unit]

  /**
   * Returns for how long the message has been in the queue.
   */
  def enqueuedFor(): F[FiniteDuration] =
    F.realTimeInstant.map { now =>
      time.Duration.between(enqueuedAt, now).toMillis().millis
    }

}
