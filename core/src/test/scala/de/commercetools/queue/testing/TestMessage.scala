package de.commercetools.queue.testing

import java.time.Instant
import cats.Order

final case class TestMessage[T](payload: T, enqueuedAt: Instant)

object TestMessage {

  implicit def order[T]: Order[TestMessage[T]] = Order.by(_.enqueuedAt.toEpochMilli())

}
