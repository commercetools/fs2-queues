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

package com.commercetools.queue.aws.sqs

import cats.effect.Async
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.functorFilter._
import cats.syntax.monadError._
import cats.syntax.option._
import cats.syntax.traverse._
import com.commercetools.queue.aws.sqs.makeQueueException
import com.commercetools.queue.{DeadletterQueueConfiguration, MalformedQueueConfigurationException, QueueAdministration, QueueConfiguration, QueueCreationConfiguration, QueueDoesNotExistException}
import software.amazon.awssdk.protocols.jsoncore.JsonNodeParser
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, DeleteQueueRequest, GetQueueAttributesRequest, QueueAttributeName, SetQueueAttributesRequest}

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class SQSAdministration[F[_]](
  client: SqsAsyncClient,
  getQueueUrl: String => F[String],
  makeDQLName: String => String
)(implicit F: Async[F])
  extends QueueAdministration[F] {

  /**
   * In SQS, a dead letter queue is a standard queue with a `RedriveAllowPolicy` attribute.
   * Its ARN will be referenced in the attributes of the source queue, so it is returned
   * after creation.
   */
  private def createDeadLetterQueue(baseName: String): F[String] = {
    val dlqName = makeDQLName(baseName)
    F.fromCompletableFuture {
      F.delay {
        client.createQueue(
          CreateQueueRequest
            .builder()
            .queueName(dlqName)
            .attributes(Map(
              QueueAttributeName.REDRIVE_ALLOW_POLICY -> """{"redrivePermission": "allowAll"}""",
              // SQS has a maximum retention period of 14 days
              // see https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_CreateQueue.html
              QueueAttributeName.MESSAGE_RETENTION_PERIOD -> 14.days.toSeconds.toString()
            ).asJava)
            .build())
      }
    }.flatMap { response =>
      F.fromCompletableFuture {
        F.delay {
          client.getQueueAttributes(
            GetQueueAttributesRequest
              .builder()
              .queueUrl(response.queueUrl())
              .attributeNames(QueueAttributeName.QUEUE_ARN)
              .build())
        }
      }.flatMap { response =>
        val arn = response.attributes().get(QueueAttributeName.QUEUE_ARN)
        F.raiseWhen(arn == null)(
          MalformedQueueConfigurationException(dlqName, QueueAttributeName.QUEUE_ARN.toString(), "<missing>"))
          .as(arn)
      }
    }
  }

  override def create(name: String, configuration: QueueCreationConfiguration): F[Unit] =
    configuration.deadletter
      .traverse(maxAttempts => createDeadLetterQueue(name).map(_ -> maxAttempts))
      .flatMap { dlq =>
        F.fromCompletableFuture {
          F.delay {
            client.createQueue(
              CreateQueueRequest
                .builder()
                .queueName(name)
                .attributes(Map(
                  QueueAttributeName.MESSAGE_RETENTION_PERIOD -> Some(configuration.messageTTL.toSeconds.toString()),
                  QueueAttributeName.VISIBILITY_TIMEOUT -> Some(configuration.lockTTL.toSeconds.toString()),
                  QueueAttributeName.REDRIVE_POLICY -> dlq.map { case (dlqArn, maxAttempts) =>
                    s"""{"deadLetterTargetArn":"$dlqArn","maxReceiveCount":$maxAttempts}"""
                  }
                ).flattenOption.asJava)
                .build())
          }
        }.void
          .adaptError(makeQueueException(_, name))
      }

  override def update(name: String, messageTTL: Option[FiniteDuration], lockTTL: Option[FiniteDuration]): F[Unit] =
    getQueueUrl(name)
      .flatMap { queueUrl =>
        F.fromCompletableFuture {
          F.delay {
            client.setQueueAttributes(
              SetQueueAttributesRequest
                .builder()
                .queueUrl(queueUrl)
                .attributes(Map(
                  QueueAttributeName.MESSAGE_RETENTION_PERIOD -> messageTTL.map(_.toSeconds.toString()),
                  QueueAttributeName.VISIBILITY_TIMEOUT -> lockTTL.map(_.toSeconds.toString())
                ).flattenOption.asJava)
                .build())
          }
        }.void
      }
      .adaptError(makeQueueException(_, name))

  override def configuration(name: String): F[QueueConfiguration] =
    getQueueUrl(name)
      .flatMap { queueUrl =>
        F.fromCompletableFuture {
          F.delay {
            client.getQueueAttributes(
              GetQueueAttributesRequest
                .builder()
                .queueUrl(queueUrl)
                .attributeNames(
                  QueueAttributeName.MESSAGE_RETENTION_PERIOD,
                  QueueAttributeName.VISIBILITY_TIMEOUT,
                  QueueAttributeName.REDRIVE_POLICY)
                .build())
          }
        }
      }
      .flatMap { response =>
        val attributes = response.attributes().asScala
        for {
          messageTTL <-
            attributes
              .get(QueueAttributeName.MESSAGE_RETENTION_PERIOD)
              .liftTo[F](MalformedQueueConfigurationException(name, "messageTTL", "<missing>"))
              .flatMap(ttl =>
                ttl.toIntOption
                  .map(_.seconds)
                  .liftTo[F](MalformedQueueConfigurationException(name, "messageTTL", ttl)))
          lockTTL <-
            attributes
              .get(QueueAttributeName.VISIBILITY_TIMEOUT)
              .liftTo[F](MalformedQueueConfigurationException(name, "lockTTL", "<missing>"))
              .flatMap(ttl =>
                ttl.toIntOption
                  .map(_.seconds)
                  .liftTo[F](MalformedQueueConfigurationException(name, "lockTTL", ttl)))
          deadletter <- attributes.get(QueueAttributeName.REDRIVE_POLICY).traverse { policy =>
            for {
              bag <- F.delay(JsonNodeParser.create().parse(policy))
              dlq <- F.delay(bag.field("deadLetterTargetArn").get().asString().split(":").last)
              maxAttempts <- F.delay(bag.field("maxReceiveCount").get().asNumber().toInt)
            } yield DeadletterQueueConfiguration(dlq, maxAttempts)
          }
        } yield QueueConfiguration(messageTTL = messageTTL, lockTTL = lockTTL, deadletter)
      }
      .adaptError(makeQueueException(_, name))

  override def delete(name: String): F[Unit] =
    getQueueUrl(name)
      .flatMap { queueUrl =>
        F.fromCompletableFuture {
          F.delay {
            client.deleteQueue(
              DeleteQueueRequest
                .builder()
                .queueUrl(queueUrl)
                .build())
          }
        }
      }
      .void
      .adaptError(makeQueueException(_, name))

  override def exists(name: String): F[Boolean] =
    getQueueUrl(name)
      .as(true)
      .recover { case _: QueueDoesNotExistException => false }
      .adaptError(makeQueueException(_, name))

}
