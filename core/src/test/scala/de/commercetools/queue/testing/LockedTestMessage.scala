package de.commercetools.queue.testing

import cats.effect.IO
import cats.effect.std.AtomicCell
import de.commercetools.queue.MessageContext

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.FiniteDuration

final case class LockedTestMessage[T](
  lock: UUID,
  msg: TestMessage[T],
  lockedUntil: Instant,
  lockTTL: FiniteDuration,
  state: AtomicCell[IO, QueueState[T]])
  extends MessageContext[T] {

  override def payload: T = msg.payload

  override def enqueuedAt: Instant = msg.enqueuedAt

  override val metadata: Map[String, String] = Map.empty

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
