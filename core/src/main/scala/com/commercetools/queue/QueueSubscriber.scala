package com.commercetools.queue

import cats.effect.Outcome
import cats.syntax.all._
import cats.effect.syntax.all._
import fs2.{Chunk, Pull, Stream}

import scala.concurrent.duration.FiniteDuration
import cats.effect.Concurrent

/**
 * The base interface to subscribe to a queue.
 */
abstract class QueueSubscriber[F[_], T](implicit F: Concurrent[F]) {

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
  def messages(batchSize: Int, waitingTime: FiniteDuration): Stream[F, MessageContext[F, T]]

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
  def processWithAutoAck[Res](batchSize: Int, waitingTime: FiniteDuration)(f: Message[T] => F[Res]): Stream[F, Res] = {
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
  def attemptProcessWithAutoAck[Res](batchSize: Int, waitingTime: FiniteDuration)(f: Message[T] => F[Res])
    : Stream[F, Either[Throwable, Res]] =
    messages(batchSize, waitingTime).parEvalMap(batchSize)(ctx =>
      f(ctx).attempt.flatTap {
        case Right(_) => ctx.ack()
        case Left(_) => ctx.nack()
      })

}
