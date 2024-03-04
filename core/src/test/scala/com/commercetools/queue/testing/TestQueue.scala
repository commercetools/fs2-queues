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

import cats.collections.Heap
import cats.data.Chain
import cats.effect.IO
import cats.effect.std.AtomicCell
import cats.syntax.traverse._
import fs2.Chunk

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration

class TestQueue[T](
  state: AtomicCell[IO, QueueState[T]],
  val messageTTL: FiniteDuration,
  val lockTTL: FiniteDuration) {

  // updates the given state with the current situation at `now`
  // this is used to have a fresh view of the queue whenever it is accessed
  // for publication, subscription, or to get the current state for checks
  private def update(state: QueueState[T]): IO[QueueState[T]] = IO.realTimeInstant.map { now =>
    // put back expired locked messages in the available messages
    val (stillLocked, toUnlock) = state.locked.partitionMap {
      case e @ (_, locked) if locked.lockedUntil.isAfter(now) => Left(e)
      case (_, locked) => Right(locked.msg)
    }
    val withUnlocked = state.available.addAll(toUnlock)
    // put delayed messages for which delay expired
    val (stillDelayed, toPublish) = state.delayed.partition(_.enqueuedAt.isAfter(now))
    val withDelayed = withUnlocked.addAll(toPublish)
    // now remove all messages for which global TTL is expired
    val withoutExpired = withDelayed.foldLeft(Heap.empty[TestMessage[T]]) { (acc, msg) =>
      if (msg.enqueuedAt.plusMillis(messageTTL.toMillis).isAfter(now)) {
        // still valid
        acc.add(msg)
      } else {
        // expired, drop it
        acc
      }
    }
    state.copy(available = withoutExpired, locked = stillLocked.toMap, delayed = stillDelayed)

  }

  def getState: IO[QueueState[T]] =
    state.evalGetAndUpdate(update(_))

  def setAvailableMessages(messages: List[TestMessage[T]]): IO[Unit] =
    state.update(_.copy(available = Heap.fromIterable(messages)))

  def getAvailableMessages: IO[List[TestMessage[T]]] =
    getState.map(_.available.toList)

  def setDelayedMessages(messages: List[TestMessage[T]]): IO[Unit] =
    state.update(_.copy(delayed = messages))

  def getDelayedMessages: IO[List[TestMessage[T]]] =
    getState.map(_.delayed.toList)

  def setLockedMessages(messages: List[LockedTestMessage[T]]): IO[Unit] =
    state.update(_.copy(locked = messages.map(m => m.lock -> m).toMap))

  def getLockedMessages: IO[List[LockedTestMessage[T]]] =
    getState.map(_.locked.values.toList)

  private def take(n: Int, available: Heap[TestMessage[T]]): (Chain[TestMessage[T]], Heap[TestMessage[T]]) = {
    @tailrec
    def loop(n: Int, available: Heap[TestMessage[T]], acc: Chain[TestMessage[T]])
      : (Chain[TestMessage[T]], Heap[TestMessage[T]]) =
      if (n <= 0) {
        (acc, available)
      } else {
        available.getMin match {
          case Some(msg) => loop(n - 1, available.remove, acc.append(msg))
          case None => (acc, available)
        }
      }
    loop(n, available, Chain.empty)
  }

  def lockMessages(n: Int): IO[Chunk[LockedTestMessage[T]]] =
    state.evalModify { state =>
      for {
        now <- IO.realTimeInstant
        state <- update(state)
        // now lock the first `batchSize` available messages
        (batch, stillAvailable) = take(n, state.available)
        // create the locked messages out of the batch
        newlyLocked <- batch
          .traverse(msg =>
            (IO.randomUUID).map(lock =>
              (
                lock,
                LockedTestMessage(
                  lock = lock,
                  msg = msg,
                  lockedUntil = now.plusMillis(lockTTL.toMillis),
                  lockTTL = lockTTL,
                  state = this.state))))
      } yield (
        state.copy(available = stillAvailable, locked = state.locked ++ newlyLocked.iterator),
        Chunk.chain(newlyLocked.map(_._2)))
    }

  def enqeueMessages(messages: List[T], delay: Option[FiniteDuration]) =
    state.evalUpdate { state =>
      for {
        now <- IO.realTimeInstant
        state <- update(state)
      } yield delay match {
        case None =>
          state.copy(available = state.available.addAll(messages.map(TestMessage(_, now))))
        case Some(delay) =>
          val delayed = now.plusMillis(delay.toMillis)
          state.copy(delayed = messages.map(TestMessage(_, delayed)) reverse_::: state.delayed)
      }
    }

}
