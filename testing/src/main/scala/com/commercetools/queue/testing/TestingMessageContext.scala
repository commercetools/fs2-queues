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

import cats.effect.IO
import com.commercetools.queue.{MessageContext, MessageId, UnsealedMessageContext}

import java.time.Instant

/**
 * A message context for testing purpose.
 */
case class TestingMessageContext[T](
  payload: T,
  enqueuedAt: Instant = Instant.EPOCH,
  messageId: MessageId = MessageId(""),
  metadata: Map[String, String] = Map.empty) {
  self =>

  /** A message context that performs the provided effects on every action. */
  def forEffects(onAck: IO[Unit], onNack: IO[Unit], onExtendLock: IO[Unit]): MessageContext[IO, T] =
    new UnsealedMessageContext[IO, T] {
      override def messageId: MessageId = self.messageId
      override def payload: IO[T] = IO.pure(self.payload)
      override def rawPayload: String = self.payload.toString()
      override def enqueuedAt: Instant = self.enqueuedAt
      override def metadata: Map[String, String] = self.metadata
      override def ack(): IO[Unit] = onAck
      override def nack(): IO[Unit] = onNack
      override def extendLock(): IO[Unit] = onExtendLock
    }

  /** A message context that does not perform anything on any action. */
  def noop: MessageContext[IO, T] =
    forEffects(IO.unit, IO.unit, IO.unit)

  /** A message context that raises the provided exception on every action. */
  def failing(t: Exception): MessageContext[IO, T] =
    forEffects(IO.raiseError(t), IO.raiseError(t), IO.raiseError(t))

  /** A message context that returns a canceled `IO` on every action. */
  def canceled: MessageContext[IO, T] =
    forEffects(IO.canceled, IO.canceled, IO.canceled)

}
