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

import cats.MonadThrow
import cats.syntax.applicativeError._
import cats.syntax.functor._

import scala.concurrent.duration.FiniteDuration

trait MessageHandler[F[_], T, Res, D[_] <: Decision[_]] {
  def handle(msg: Message[F, T]): F[D[Res]]
}

sealed trait Decision[+O]
sealed trait ImmediateDecision[+O] extends Decision[O]
object Decision {
  case class Ok[O](res: O) extends ImmediateDecision[O]
  case object Drop extends ImmediateDecision[Nothing]
  case class Fail(t: Throwable, ack: Boolean) extends ImmediateDecision[Nothing]
  case class Reenqueue(metadata: Option[Map[String, String]], delay: Option[FiniteDuration]) extends Decision[Nothing]
}

object MessageHandler {
  // nack on any failure except for deserialization exception
  def default[F[_]: MonadThrow, T, O](f: Message[F, T] => F[O]): MessageHandler[F, T, O, ImmediateDecision] =
    msg =>
      f(msg).attempt.map {
        case Left(de: DeserializationException) => Decision.Fail(de, ack = true)
        case Left(t) => Decision.Fail(t, ack = false)
        case Right(a) => Decision.Ok(a)
      }
}
