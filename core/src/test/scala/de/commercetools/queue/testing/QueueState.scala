package de.commercetools.queue.testing

import cats.collections.Heap

import java.util.UUID

final case class QueueState[T](
  available: Heap[TestMessage[T]],
  delayed: List[TestMessage[T]],
  locked: Map[UUID, LockedTestMessage[T]])
