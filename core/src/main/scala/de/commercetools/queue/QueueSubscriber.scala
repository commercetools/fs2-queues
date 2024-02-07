package de.commercetools.queue

import fs2.Stream
import cats.effect.IO
import scala.concurrent.duration.FiniteDuration

/**
 * The base interface to subscribe to a queue.
 */
trait QueueSubscriber[T] {

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
  def messages(batchSize: Int, waitingTime: FiniteDuration): Stream[IO, MessageContext[T]]

  /**
   * Processes the messages with the provided processing function.
   * The messages are automatically ack'ed on success and nack'ed on error,
   * and the results are emitted down-stream.
   * The stream stops on the first failed processing and fails with the
   * processing error. If you want to implement error recovery, you might want
   * to use `attemptProcessWithAutoAck` instead.
   *
   * '''Note:''' This stream does '''not''' handle lock extension.
   */
  def processWithAutoAck[Res](batchSize: Int, waitingTime: FiniteDuration)(f: T => IO[Res]): Stream[IO, Res] =
    messages(batchSize, waitingTime).evalMap(ctx => f(ctx.payload).flatTap(_ => ctx.ack()).onError(_ => ctx.nack()))

  /**
   * Processes the messages with the provided processing function.
   * The messages are automatically ack'ed on success and nack'ed on error.
   * The stream emits results or errors down-stream and does not fail on
   * processing errors, allowing you to build error recovery logic.
   *
   * '''Note:''' This stream does '''not''' handle lock extension.
   */
  def attemptProcessWithAutoAck[Res](batchSize: Int, waitingTime: FiniteDuration)(f: T => IO[Res])
    : Stream[IO, Either[Throwable, Res]] =
    messages(batchSize, waitingTime).evalMapChunk(ctx =>
      f(ctx.payload).attempt.flatTap {
        case Right(_) => ctx.ack()
        case Left(_) => ctx.nack()
      })

}
