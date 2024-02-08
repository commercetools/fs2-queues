package de.commercetools.queue.aws.sqs

import cats.effect.IO
import de.commercetools.queue.QueueAdministration
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, QueueAttributeName}

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException

class SQSAdministration(client: SqsAsyncClient, getQueueUrl: String => IO[String]) extends QueueAdministration {

  override def create(name: String, messageTTL: FiniteDuration, lockTTL: FiniteDuration): IO[Unit] =
    IO.fromCompletableFuture {
      IO {
        client.createQueue(
          CreateQueueRequest
            .builder()
            .queueName(name)
            .attributes(Map(
              QueueAttributeName.MESSAGE_RETENTION_PERIOD -> messageTTL.toSeconds.toString(),
              QueueAttributeName.VISIBILITY_TIMEOUT -> lockTTL.toSeconds.toString()).asJava)
            .build())
      }
    }.void

  override def delete(name: String): IO[Unit] =
    getQueueUrl(name).flatMap { queueUrl =>
      IO.fromCompletableFuture {
        IO {
          client.deleteQueue(
            DeleteQueueRequest
              .builder()
              .queueUrl(queueUrl)
              .build())
        }
      }
    }.void

  override def exists(name: String): IO[Boolean] =
    getQueueUrl(name).as(true).recover { _: QueueDoesNotExistException => false }

}
