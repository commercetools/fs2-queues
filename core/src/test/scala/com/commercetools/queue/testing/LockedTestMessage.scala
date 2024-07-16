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
import cats.effect.std.AtomicCell
import com.commercetools.queue.UnsealedMessageContext

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.FiniteDuration

final case class LockedTestMessage[T](
  lock: UUID,
  msg: TestMessage[T],
  lockedUntil: Instant,
  lockTTL: FiniteDuration,
  state: AtomicCell[IO, QueueState[T]])
  extends UnsealedMessageContext[IO, T] {

  override def messageId: String = lock.toString

  override def payload: IO[T] = IO.pure(msg.payload)

  override def rawPayload: String = msg.payload.toString()

  override def enqueuedAt: Instant = msg.enqueuedAt

  override val metadata: Map[String, String] = msg.metadata

  override def ack(): IO[Unit] =
    // done, just delete it
    state.update { state =>
      state.copy(locked = state.locked.removed(lock))
    }

  override def nack(): IO[Unit] =
    // move it back to available
    state.update { state =>
      state.locked.get(lock) match {
        case Some(msg) =>
          state.copy(locked = state.locked.removed(lock), available = state.available.add(msg.msg))
        case None =>
          state
      }
    }

  override def extendLock(): IO[Unit] =
    state.evalUpdate { state =>
      state.locked.get(lock) match {
        case Some(msg) =>
          IO.realTimeInstant.map { now =>
            state.copy(locked = state.locked.updated(lock, msg.copy(lockedUntil = now.plusMillis(lockTTL.toMillis))))
          }
        case None =>
          IO.pure(state)
      }
    }

}
