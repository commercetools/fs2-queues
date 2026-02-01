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

package com.commercetools.queue.gcp

import cats.effect.Async
import cats.syntax.either._
import cats.syntax.functor._
import com.commercetools.queue._
import com.google.api.core.{ApiFuture, ApiFutureCallback, ApiFutures}
import com.google.api.gax.retrying.RetrySettings
import com.google.api.gax.rpc.{AlreadyExistsException, NotFoundException}
import com.google.common.util.concurrent.MoreExecutors

import java.time.{Duration, Instant}
import scala.concurrent.duration._

package object pubsub {

  private[pubsub] object ToInstant {
    def unapply(s: String): Option[Instant] =
      Either.catchNonFatal(Instant.parse(s)).toOption
  }

  final private[pubsub] val delayAttribute = "com.commercetools.queue.delay"

  private[pubsub] def wrapFuture[F[_], T](future: F[ApiFuture[T]])(implicit F: Async[F]): F[T] =
    F.async { cb =>
      future.map { future =>
        ApiFutures.addCallback(
          future,
          new ApiFutureCallback[T] {

            override def onFailure(t: Throwable): Unit = cb(Left(t))

            override def onSuccess(result: T): Unit = cb(Right(result))

          },
          MoreExecutors.directExecutor()
        )
        Some(F.delay(future.cancel(false)).void)
      }
    }

  private[pubsub] def makeQueueException(t: Throwable, queueName: String): QueueException = t match {
    case _: NotFoundException => QueueDoesNotExistException(queueName, t)
    case _: AlreadyExistsException => QueueAlreadyExistException(queueName, t)
    case t: QueueException => t
    case _ => UnknownQueueException(queueName, t)
  }

  private[pubsub] def makePushQueueException(t: Throwable, queueName: String): QueueException =
    new CannotPushException(queueName, makeQueueException(t, queueName))

  private[pubsub] def makePullQueueException(t: Throwable, queueName: String): QueueException =
    t match {
      case t: QueueException => t
      case _ => new CannotPullException(queueName, makeQueueException(t, queueName))
    }

  private[pubsub] def makeMessageException(t: Throwable, queueName: String, msgId: MessageId, action: Action)
    : QueueException =
    t match {
      case t: QueueException => t
      case _ => new MessageException(msgId = msgId, action = action, inner = makeQueueException(t, queueName))
    }

  private[pubsub] val ackRetrySettings: RetrySettings =
    RetrySettings
      .newBuilder()
      .setInitialRpcTimeoutDuration(Duration.ofMillis(10.seconds.toMillis))
      .setMaxRpcTimeoutDuration(Duration.ofMillis(30.seconds.toMillis))
      .setTotalTimeoutDuration(Duration.ofMillis(30.seconds.toMillis))
      .build()
}
