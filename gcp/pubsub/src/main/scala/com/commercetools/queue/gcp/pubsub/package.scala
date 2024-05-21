package com.commercetools.queue.gcp

import cats.effect.Async
import cats.syntax.functor._
import com.commercetools.queue.{Action, CannotPullException, CannotPushException, MessageException, QueueAlreadyExistException, QueueDoesNotExistException, QueueException, UnknownQueueException}
import com.google.api.core.{ApiFuture, ApiFutureCallback, ApiFutures}
import com.google.api.gax.rpc.{AlreadyExistsException, NotFoundException}
import com.google.common.util.concurrent.MoreExecutors
import java.time.Instant
import cats.syntax.either._

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

  def makeQueueException(t: Throwable, queueName: String): QueueException = t match {
    case _: NotFoundException => QueueDoesNotExistException(queueName, t)
    case _: AlreadyExistsException => QueueAlreadyExistException(queueName, t)
    case t: QueueException => t
    case _ => UnknownQueueException(queueName, t)
  }

  def makePushQueueException(t: Throwable, queueName: String): QueueException =
    new CannotPushException(queueName, makeQueueException(t, queueName))

  def makePullQueueException(t: Throwable, queueName: String): QueueException =
    t match {
      case t: QueueException => t
      case _ => new CannotPullException(queueName, makeQueueException(t, queueName))
    }

  def makeMessageException(t: Throwable, queueName: String, msgId: String, action: Action): QueueException =
    t match {
      case t: QueueException => t
      case _ => new MessageException(msgId = msgId, action = action, inner = makeQueueException(t, queueName))
    }

}
