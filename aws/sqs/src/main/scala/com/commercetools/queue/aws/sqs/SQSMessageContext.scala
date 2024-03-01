package com.commercetools.queue.aws.sqs

import cats.effect.Async
import cats.syntax.functor._
import com.commercetools.queue.MessageContext
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{ChangeMessageVisibilityRequest, DeleteMessageRequest}

import java.time.Instant

class SQSMessageContext[F[_], T](
  val payload: T,
  val enqueuedAt: Instant,
  val metadata: Map[String, String],
  receiptHandle: String,
  val messageId: String,
  lockTTL: Int,
  queueUrl: String,
  client: SqsAsyncClient
)(implicit F: Async[F])
  extends MessageContext[F, T] {

  override def ack(): F[Unit] =
    F.fromCompletableFuture {
      F.delay {
        client.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(receiptHandle).build())
      }
    }.void

  override def nack(): F[Unit] =
    F.fromCompletableFuture {
      F.delay {
        client.changeMessageVisibility(
          ChangeMessageVisibilityRequest
            .builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .visibilityTimeout(0)
            .build())
      }
    }.void

  override def extendLock(): F[Unit] =
    F.fromCompletableFuture {
      F.delay {
        client.changeMessageVisibility(
          ChangeMessageVisibilityRequest
            .builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .visibilityTimeout(lockTTL)
            .build())
      }
    }.void

}
