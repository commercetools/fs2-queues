package de.commercetools.queue.aws.sqs

import scala.concurrent.duration.FiniteDuration
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import de.commercetools.queue.MessageContext
import java.time.Instant
import cats.effect.IO
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest

class SQSMessageContext[T](
  val payload: T,
  val enqueuedAt: Instant,
  val metadata: Map[String, String],
  receiptHandle: String,
  lockTTL: FiniteDuration,
  queueUrl: String,
  client: SqsAsyncClient)
  extends MessageContext[T] {

  override def ack(): IO[Unit] =
    IO.fromCompletableFuture {
      IO {
        client.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(receiptHandle).build())
      }
    }.void

  override def nack(): IO[Unit] =
    IO.fromCompletableFuture {
      IO {
        client.changeMessageVisibility(
          ChangeMessageVisibilityRequest
            .builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .visibilityTimeout(0)
            .build())
      }
    }.void

  override def extendLock(): IO[Unit] =
    IO.fromCompletableFuture {
      IO {
        client.changeMessageVisibility(
          ChangeMessageVisibilityRequest
            .builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .visibilityTimeout(lockTTL.toSeconds.toInt)
            .build())
      }
    }.void

}
