package de.commercetools.queue.aws.sqs

import cats.effect.IO
import de.commercetools.queue.{Deserializer, MessageContext, QueueSubscriber}
import fs2.{Chunk, Stream}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{GetQueueAttributesRequest, MessageSystemAttributeName, QueueAttributeName, ReceiveMessageRequest}

import java.time.Instant
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class SQSSubscriber[T](
  getQueueUrl: IO[String],
  client: SqsAsyncClient
)(implicit deserializer: Deserializer[T])
  extends QueueSubscriber[T] {

  private def getLockTTL(queueUrl: String): IO[Int] =
    IO.fromCompletableFuture {
      IO {
        client.getQueueAttributes(
          GetQueueAttributesRequest
            .builder()
            .queueUrl(queueUrl)
            .attributeNames(QueueAttributeName.VISIBILITY_TIMEOUT)
            .build())
      }
    }.map(_.attributes().get(QueueAttributeName.VISIBILITY_TIMEOUT).toInt)

  override def messages(batchSize: Int, waitingTime: FiniteDuration): Stream[IO, MessageContext[T]] =
    Stream.eval(getQueueUrl).flatMap { queueUrl =>
      Stream.eval(getLockTTL(queueUrl)).flatMap { lockTTL =>
        Stream
          .repeatEval(IO.fromCompletableFuture {
            IO {
              // visibility timeout is at queue creation time
              client.receiveMessage(
                ReceiveMessageRequest
                  .builder()
                  .queueUrl(queueUrl)
                  .maxNumberOfMessages(batchSize)
                  .waitTimeSeconds(waitingTime.toSeconds.toInt)
                  .attributeNamesWithStrings(MessageSystemAttributeName.SENT_TIMESTAMP.toString())
                  .build())
            }
          })
          .map { messages =>
            Chunk.from(messages.messages().asScala)
          }
          .unchunks
          .evalMap { message =>
            for {
              sentTimestamp <- IO(
                Instant.ofEpochMilli(message.attributes().get(MessageSystemAttributeName.SENT_TIMESTAMP).toLong))
              data <- deserializer.deserialize(message.body())
            } yield new SQSMessageContext(
              data,
              sentTimestamp,
              message.attributesAsStrings().asScala.toMap,
              message.receiptHandle(),
              message.messageId(),
              lockTTL,
              queueUrl,
              client)
          }
      }
    }

}
