package de.commercetools.queue.aws.sqs

import cats.effect.IO
import de.commercetools.queue.{QueuePublisher, Serializer}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

import scala.concurrent.duration.FiniteDuration
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry

class SQSPublisher[T](queueUrl: String, client: SqsAsyncClient)(implicit serializer: Serializer[T])
  extends QueuePublisher[T] {

  override def publish(message: T, delay: Option[FiniteDuration]): IO[Unit] =
    IO.fromCompletableFuture {
      IO {
        client.sendMessage(
          SendMessageRequest
            .builder()
            .queueUrl(queueUrl)
            .messageBody(serializer.serialize(message))
            .delaySeconds(delay.fold(0)(_.toSeconds.toInt))
            .build())
      }
    }.void

  override def publish(messages: List[T], delay: Option[FiniteDuration]): IO[Unit] =
    IO.fromCompletableFuture {
      IO {
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
