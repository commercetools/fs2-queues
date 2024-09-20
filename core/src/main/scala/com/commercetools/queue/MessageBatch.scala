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

/**
 * Interface to interact with the message received from a queue
 * as a single batch allowing user to ack/nack all messages in a single
 * call if the underlying implementation supports for it.
 *
 * For implementations that do not support batched acknowledging both
 * `ackAll` and `nackAll` methods do not guarantee atomicity and will
 * fallback to using per-message calls.
 */
trait MessageBatch[F[_], T] {
  def messages: Chunk[Message[F, T]]

  /**
   * Acknowledges all the messages in the chunk.
   */
  def ackAll: F[Unit]

  /**
   * Mark all messages from the chunk as non acknowledged.
   */
  def nackAll: F[Unit]
}
