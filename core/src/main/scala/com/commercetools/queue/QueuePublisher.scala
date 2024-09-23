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

import cats.effect.{MonadCancel, Resource}
import fs2.Stream

import scala.concurrent.duration.FiniteDuration

/**
 * The interface to publish to a queue.
 */
sealed abstract class QueuePublisher[F[_], T](implicit F: MonadCancel[F, Throwable]) {

  /** The queue name to which this publisher publishes. */
  def queueName: String

  /**
   * Returns a way to push messages into the queue.
   * This is a low-level construct, mainly aiming at integrating existing
   * code bases that require to push explicitly.
   *
   * '''Note:''' Prefer using the sinks below when possible.
   */
  def pusher: Resource[F, QueuePusher[F, T]]

  /**
   * Sink to pipe your [[fs2.Stream Stream]] into, in order to publish
   * produced data to the queue. The messages are published in batches, according
   * to the `batchSize` parameter.
   */
  def sink(batchSize: Int = 10, delay: Option[FiniteDuration] = None)(upstream: Stream[F, (T, Map[String, String])])
    : Stream[F, Nothing] =
    Stream.resource(pusher).flatMap { pusher =>
      upstream.chunkN(batchSize).foreach { chunk =>
        pusher.push(chunk.toList, delay)
      }
    }

}

abstract private[queue] class UnsealedQueuePublisher[F[_], T](implicit F: MonadCancel[F, Throwable])
  extends QueuePublisher[F, T]

object QueuePublisher {

  /**
   * A publisher that does nothing.
   */
  def noop[F[_], T](implicit F: MonadCancel[F, Throwable]) = new QueuePublisher[F, T] {
    override def queueName: String = ""
    override def pusher: Resource[F, QueuePusher[F, T]] = Resource.pure(QueuePusher.noop)
  }
}
