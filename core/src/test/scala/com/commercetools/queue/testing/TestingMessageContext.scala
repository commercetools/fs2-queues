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
import com.commercetools.queue.MessageContext

import java.time.Instant

case class TestingMessageContext[T](
  payload: T,
  enqueuedAt: Instant = Instant.EPOCH,
  messageId: String = "",
  metadata: Map[String, String] = Map.empty) {
  self =>

  def noop: MessageContext[IO, T] = new MessageContext[IO, T] {
    override def messageId: String = self.messageId
    override def payload: T = self.payload
    override def enqueuedAt: Instant = self.enqueuedAt
    override def metadata: Map[String, String] = self.metadata
    override def ack(): IO[Unit] = IO.unit
    override def nack(): IO[Unit] = IO.unit
    override def extendLock(): IO[Unit] = IO.unit
  }

  def failing(t: Exception): MessageContext[IO, T] = new MessageContext[IO, T] {
    override def messageId: String = self.messageId
    override def payload: T = self.payload
    override def enqueuedAt: Instant = self.enqueuedAt
    override def metadata: Map[String, String] = self.metadata
    override def ack(): IO[Unit] = IO.raiseError(t)
    override def nack(): IO[Unit] = IO.raiseError(t)
    override def extendLock(): IO[Unit] = IO.raiseError(t)
  }

  def canceled: MessageContext[IO, T] = new MessageContext[IO, T] {
    override def messageId: String = self.messageId
    override def payload: T = self.payload
    override def enqueuedAt: Instant = self.enqueuedAt
    override def metadata: Map[String, String] = self.metadata
    override def ack(): IO[Unit] = IO.canceled
    override def nack(): IO[Unit] = IO.canceled
    override def extendLock(): IO[Unit] = IO.canceled
  }

}
