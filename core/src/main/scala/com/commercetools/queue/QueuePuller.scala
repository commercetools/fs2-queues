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

import fs2.Chunk

import scala.concurrent.duration.FiniteDuration

/**
 * A queue puller allows for pulling batches of elements from a queue individually.
 */
sealed trait QueuePuller[F[_], T] {

  /** The queue name from which this puller is pulling. */
  def queueName: String

  /**
   * Pulls one batch of messages from the underlying queue system.
   *
   * The method returns chunks of size `batchSize` max, and (semantically)
   * blocks for `waitingTime` before returning any messages. The chunk might be empty
   * if no new messages are available during the waiting time.
   *
   * '''Note:''' the messages returned by this method must be manually
   * managed (ack'ed, nack'ed, extended). This is useful to provide fine
   * grained control over message lifecycle from the app point of view.
   * If you have simpler workflows, please refer to the other subscriber
   * methods.
   */
  def pullBatch(batchSize: Int, waitingTime: FiniteDuration): F[Chunk[MessageContext[F, T]]]

  /**
   * Pulls batch of messages with the same semantics as `pullBatch`
   * with the difference in message lifecycle control. Messages pulled this
   * way can only be managed (ack'ed, nack'ed) in bulk by batching acknowledgements
   * if the underlying implementation supports it, otherwise it falls back to
   * non-atomic, per-message management.
   */
  def pullMessageBatch(batchSize: Int, waitingTime: FiniteDuration): F[MessageBatch[F, T]]

}

private[queue] trait UnsealedQueuePuller[F[_], T] extends QueuePuller[F, T]
