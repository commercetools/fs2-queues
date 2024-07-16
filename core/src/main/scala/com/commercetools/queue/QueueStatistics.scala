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

import cats.effect.{Resource, Temporal}
import cats.syntax.applicativeError._
import fs2.Stream

import scala.concurrent.duration.FiniteDuration

/**
 * The base interface to fetch statistics from a queue.
 */
sealed abstract class QueueStatistics[F[_]](implicit F: Temporal[F]) {

  /** The queue name from which to pull statistics. */
  def queueName: String

  /**
   * Returns a way to fetch statistics for a queue.
   * This is a low-level construct mainly aiming at integrating with existing
   * code bases that require to fetch statistics explicitly.
   *
   * '''Note:''' Prefer using the `stream` below when possible.
   */
  def fetcher: Resource[F, QueueStatsFetcher[F]]

  /**
   * Stream emitting statistics every configured interval.
   * The stream will not fail if an attempt to fetch statistics fails.
   * This gives the freedom to the caller to implement its own error handling mechanism.
   *
   * @param interval the emission interval
   */
  def stream(interval: FiniteDuration): Stream[F, Either[Throwable, QueueStats]] =
    Stream.resource(fetcher).flatMap { fetcher =>
      Stream
        .repeatEval(fetcher.fetch.attempt)
        .meteredStartImmediately(interval)
    }

  /**
   * Strict version of `stream`, that fails upon the first fetch failure.
   *
   * @param interval the emission interval
   */
  def strictStream(interval: FiniteDuration): Stream[F, QueueStats] =
    stream(interval).rethrow

}

abstract private[queue] class UnsealedQueueStatistics[F[_]](implicit F: Temporal[F]) extends QueueStatistics[F]
