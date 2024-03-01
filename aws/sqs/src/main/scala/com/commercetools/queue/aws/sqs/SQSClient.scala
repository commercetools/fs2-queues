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

import cats.effect.{Async, Resource}
import cats.syntax.functor._
import com.commercetools.queue.{Deserializer, QueueAdministration, QueueClient, QueuePublisher, QueueSubscriber, Serializer}
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest

import java.net.URI

class SQSClient[F[_]] private (client: SqsAsyncClient)(implicit F: Async[F]) extends QueueClient[F] {

  private def getQueueUrl(name: String): F[String] =
    F.fromCompletableFuture {
      F.delay {
        client.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build())
      }
    }.map(_.queueUrl)

  override def administration: QueueAdministration[F] =
    new SQSAdministration(client, getQueueUrl(_))

  override def publisher[T: Serializer](name: String): Resource[F, QueuePublisher[F, T]] =
    Resource.eval(getQueueUrl(name).map(new SQSPublisher(_, client)))

  override def subscriber[T: Deserializer](name: String): QueueSubscriber[F, T] =
    new SQSSubscriber[F, T](getQueueUrl(name), client)

}

object SQSClient {

  def apply[F[_]](
    region: Region,
    credentials: AwsCredentialsProvider,
    endpoint: Option[URI] = None,
    httpClient: Option[SdkAsyncHttpClient] = None
  )(implicit F: Async[F]
  ): Resource[F, SQSClient[F]] =
    Resource
      .fromAutoCloseable {
        F.delay {
          val builder =
            SqsAsyncClient.builder().region(region).credentialsProvider(credentials)

          endpoint.foreach(builder.endpointOverride(_))

          httpClient.foreach(builder.httpClient(_))

          builder.build()
        }
      }
      .map(new SQSClient(_))

}
