package de.commercetools.queue.aws.sqs

import cats.effect.IO
import de.commercetools.queue.MessageContext
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{ChangeMessageVisibilityRequest, DeleteMessageRequest}

import java.time.Instant

class SQSMessageContext[T](
  val payload: T,
  val enqueuedAt: Instant,
  val metadata: Map[String, String],
  receiptHandle: String,
  messageId: String,
  lockTTL: Int,
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
            .visibilityTimeout(lockTTL)
            .build())
      }
    }.void

  override def messageId(): String = messageId
  
}
