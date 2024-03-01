package com.commercetools.queue.aws.sqs

import com.commercetools.queue.{QueuePublisher, Serializer}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

import scala.concurrent.duration.FiniteDuration
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import cats.effect.Async
import cats.syntax.functor._

class SQSPublisher[F[_], T](queueUrl: String, client: SqsAsyncClient)(implicit F: Async[F], serializer: Serializer[T])
  extends QueuePublisher[F, T] {

  override def publish(message: T, delay: Option[FiniteDuration]): F[Unit] =
    F.fromCompletableFuture {
      F.delay {
        client.sendMessage(
          SendMessageRequest
            .builder()
            .queueUrl(queueUrl)
            .messageBody(serializer.serialize(message))
            .delaySeconds(delay.fold(0)(_.toSeconds.toInt))
            .build())
      }
    }.void

  override def publish(messages: List[T], delay: Option[FiniteDuration]): F[Unit] =
    F.fromCompletableFuture {
      F.delay {
        val delaySeconds = delay.fold(0)(_.toSeconds.toInt)
        client.sendMessageBatch(
          SendMessageBatchRequest
            .builder()
            .queueUrl(queueUrl)
            .entries(messages.map { message =>
              SendMessageBatchRequestEntry
                .builder()
                .messageBody(serializer.serialize(message))
                .delaySeconds(delaySeconds)
                .build()
            }: _*)
            .build())
      }
    }.void

}
