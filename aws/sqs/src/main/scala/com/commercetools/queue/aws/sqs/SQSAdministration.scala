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
import com.commercetools.queue.aws.sqs.makeQueueException
import com.commercetools.queue.{MalformedQueueConfigurationException, QueueConfiguration, QueueDoesNotExistException, UnsealedQueueAdministration}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, DeleteQueueRequest, GetQueueAttributesRequest, QueueAttributeName, SetQueueAttributesRequest}

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

private class SQSAdministration[F[_]](client: SqsAsyncClient, getQueueUrl: String => F[String])(implicit F: Async[F])
  extends UnsealedQueueAdministration[F] {

  override def create(name: String, messageTTL: FiniteDuration, lockTTL: FiniteDuration): F[Unit] =
    F.fromCompletableFuture {
      F.delay {
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
      .adaptError(makeQueueException(_, name))

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
                .attributeNames(QueueAttributeName.MESSAGE_RETENTION_PERIOD, QueueAttributeName.VISIBILITY_TIMEOUT)
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
        } yield QueueConfiguration(messageTTL = messageTTL, lockTTL = lockTTL)
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
