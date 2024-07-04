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
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.monadError._
import cats.syntax.option._
import cats.syntax.traverse._
import com.commercetools.queue.{MalformedQueueConfigurationException, QueueStats, QueueStatsFetcher}
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{GetQueueAttributesRequest, QueueAttributeName}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

class SQSStatisticsFetcher[F[_]](val queueName: String, client: SqsAsyncClient, queueUrl: String)(implicit F: Async[F])
  extends QueueStatsFetcher[F] {

  override def fetch: F[QueueStats] =
    (for {
      response <- F.fromCompletableFuture {
        F.delay {
          client.getQueueAttributes(
            GetQueueAttributesRequest
              .builder()
              .queueUrl(queueUrl)
              .attributeNames(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED,
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE
              )
              .build())
        }
      }
      attributes = response.attributes().asScala
      messages <- attributeAsInt(attributes, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)(queueName, "messages")
      inflight <- attributeAsIntOpt(attributes, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)(
        queueName,
        "inflight")
      delayed <- attributeAsIntOpt(attributes, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED)(
        queueName,
        "delayed")
    } yield QueueStats(messages, inflight, delayed)).adaptError(makeQueueException(_, queueName))

  private def attributeAsIntOpt(
    attributes: mutable.Map[QueueAttributeName, String],
    attribute: QueueAttributeName
  )(queueName: String,
    attributeName: String
  ): F[Option[Int]] =
    attributes
      .get(attribute)
      .traverse(raw => raw.toIntOption.liftTo[F](MalformedQueueConfigurationException(queueName, attributeName, raw)))

  private def attributeAsInt(
    attributes: mutable.Map[QueueAttributeName, String],
    attribute: QueueAttributeName
  )(queueName: String,
    attributeName: String
  ): F[Int] =
    attributeAsIntOpt(attributes, attribute)(queueName, attributeName).flatMap(
      F.fromOption(_, MalformedQueueConfigurationException(queueName, attribute.toString(), "<missing>")))

}
