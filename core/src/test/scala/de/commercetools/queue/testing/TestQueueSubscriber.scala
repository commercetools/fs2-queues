package de.commercetools.queue.testing

import cats.effect.IO
import de.commercetools.queue.{MessageContext, QueueSubscriber}
import fs2.Stream

import scala.concurrent.duration.FiniteDuration

class TestQueueSubscriber[T](queue: TestQueue[T]) extends QueueSubscriber[T] {

  override def messages(batchSize: Int, waitingTime: FiniteDuration): fs2.Stream[IO, MessageContext[T]] =
    (Stream.sleep_[IO](waitingTime) ++
      Stream
        .eval(queue.lockMessages(batchSize))
        .unchunks).repeat

}
