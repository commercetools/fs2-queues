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

import java.time.Instant

case class MessageId(value: String) extends AnyVal

/**
 * Interface to access message data received from a queue.
 */
trait Message[F[_], T] {

  /**
   * Unique message identifier
   */
  def messageId: MessageId

  /**
   * The deserialized message payload
   */
  def payload: F[T]

  /**
   * The raw message content
   */
  def rawPayload: String

  /**
   * When the message was put into the queue.
   */
  def enqueuedAt: Instant

  /**
   * Raw message metadata (depending on the underlying queue system).
   */
  def metadata: Map[String, String]

}
