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

import cats.effect.syntax.all._
import cats.effect.{Concurrent, Outcome, Resource}
import cats.syntax.all._
import fs2.{Chunk, Pull, Stream}

import scala.concurrent.duration.FiniteDuration

/**
 * The base interface to subscribe to a queue.
 */
abstract class QueueSubscriber[F[_], T](implicit F: Concurrent[F]) {

  /** The queue name to which this subscriber subscribes. */
  def queueName: String

  /**
   * Returns a way to pull batches from the queue.
   * This is a low-level construct mainly aiming at integrating with existing
   * code bases that require to pull explicitly.
   *
   * '''Note:''' Prefer using one of the `process` variant below when possible.
   */
  def puller: Resource[F, QueuePuller[F, T]]

  /**
   * The stream of messages published in the subscribed queue.
   * The [[MessageContext]] gives an interface to interact with the
   * message.
   *
   * The stream emits chunks of size `batchSize` max, and waits for
   * elements during `waitingTime` before emitting a chunk.
   *
   * '''Note:''' the messages returned by this stream must be manually
   * managed (ack'ed, nack'ed, extended). This is useful to provide fine
   * grained control over message lifecycle from the app point of view.
   * If you have simpler workflows, please refer to the other subscriber
   * methods.
   */
  final def messages(batchSize: Int, waitingTime: FiniteDuration): Stream[F, MessageContext[F, T]] =
    Stream.resource(puller).flatMap { puller =>
      Stream.repeatEval(puller.pullBatch(batchSize, waitingTime)).unchunks
    }

  /**
   * Processes the messages with the provided processing function.
   * The messages are automatically ack'ed on success and nack'ed on error,
   * and the results are emitted down-stream.
   * The stream stops on the first failed processing and fails with the
   * processing error. If you want to implement error recovery, you might want
   * to use `attemptProcessWithAutoAck` instead.
   *
   * '''Note:''' This stream does '''not''' handle lock extension.
   *
   * Messages in a batch are processed sequentially, stopping at the first error.
   * All results up to the error will be emitted downstream before failing.
   */
  final def processWithAutoAck[Res](batchSize: Int, waitingTime: FiniteDuration)(f: Message[F, T] => F[Res])
    : Stream[F, Res] = {
    // to have full control over nacking things in time after a failure, and emitting
    // results up to the error, we resort to a `Pull`, which allows this fine graind control
    // over pulling/emitting/failing
    def doChunk(chunk: Chunk[MessageContext[F, T]], idx: Int): Pull[F, Res, Unit] =
      if (idx >= chunk.size) {
        // we are done, emit the chunk
        Pull.done

      } else {
        val ctx = chunk(idx)
        Pull
          .eval(f(ctx).guaranteeCase {
            case Outcome.Succeeded(_) => ctx.ack()
            case _ =>
              // if it was cancelled or errored, let's nack this and up to the end of the chunk
              // before failing
              // array slice creation is O(1) as well as `drop` on an `ArraySlice`
              chunk.toArraySlice.drop(idx).traverse_(_.nack().attempt)
          })
          .attempt
          .flatMap {
            case Right(res) => Pull.output1(res) >> doChunk(chunk, idx + 1)
            case Left(t) =>
              // one processing failed
              Pull.raiseError[F](t)
          }
      }
    messages(batchSize, waitingTime).repeatPull(_.uncons.flatMap {
      case Some((hd, tl)) => doChunk(hd, 0).as(Some(tl))
      case None => Pull.pure(None)
    })
  }

  /**
   * Processes the messages with the provided processing function.
   * The messages are automatically ack'ed on success and nack'ed on error.
   * The stream emits results or errors down-stream and does not fail on
   * processing errors, allowing you to build error recovery logic.
   *
   * '''Note:''' This stream does '''not''' handle lock extension.
   *
   * Messages in a batch are processed in parallel but result is emitted in
   * order the messages were received.
   */
  final def attemptProcessWithAutoAck[Res](batchSize: Int, waitingTime: FiniteDuration)(f: Message[F, T] => F[Res])
    : Stream[F, Either[Throwable, Res]] =
    messages(batchSize, waitingTime).parEvalMap(batchSize)(ctx =>
      f(ctx).attempt.flatTap {
        case Right(_) => ctx.ack()
        case Left(_) => ctx.nack()
      })

  /**
   * Processes the messages with the provided message handler.
   * The messages are ack'ed, nack'ed or reenqueu'ed based on the decision returned from the handler.
   * The stream emits results or errors down-stream and does not fail on business logic errors,
   * allowing you to build error recovery logic.
   *
   * Messages in a batch are processed in parallel but result is emitted in order the messages were received,
   * with the exclusion of the messages that have been reenqueu'ed and dropped.
   */
  final def process[Res](
    batchSize: Int,
    waitingTime: FiniteDuration,
    publisherForReenqueue: QueuePublisher[F, T]
  )(handler: MessageHandler[F, T, Res, Decision]
  ): Stream[F, Either[Throwable, Res]] =
    Stream
      .resource(publisherForReenqueue.pusher)
      .flatMap { pusher =>
        messages(batchSize, waitingTime)
          .parEvalMap(batchSize) { ctx =>
            handler.handle(ctx).flatMap[Option[Either[Throwable, Res]]] {
              case Decision.Ok(res) => ctx.ack().as(res.asRight.some)
              case Decision.Drop => ctx.ack().as(none)
              case Decision.Fail(t, true) => ctx.ack().as(t.asLeft.some)
              case Decision.Fail(t, false) => ctx.nack().as(t.asLeft.some)
              case Decision.Reenqueue(metadata, delay) =>
                ctx.payload.flatMap(pusher.push(_, metadata.getOrElse(ctx.metadata), delay)).as(none)
            }
          }
          .flattenOption
      }

  /**
   * Processes the messages with the provided message handler.
   * The messages are ack'ed or nack'ed based on the decision returned from the handler.
   * The stream emits results or errors down-stream and does not fail on business logic errors,
   * allowing you to build error recovery logic.
   *
   * Messages in a batch are processed in parallel but result is emitted in order the messages were received,
   * with the exclusion of the messages that have been dropped.
   */
  final def processWithImmediateDecision[Res](
    batchSize: Int,
    waitingTime: FiniteDuration
  )(handler: MessageHandler[F, T, Res, ImmediateDecision]
  ): Stream[F, Either[Throwable, Res]] =
    process[Res](batchSize, waitingTime, QueuePublisher.noOp)((msg: Message[F, T]) =>
      handler.handle(msg).widen[Decision[Res]])
}
