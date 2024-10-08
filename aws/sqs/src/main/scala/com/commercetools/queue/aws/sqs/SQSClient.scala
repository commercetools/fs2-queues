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
import cats.syntax.monadError._
import com.commercetools.queue.{Deserializer, QueueAdministration, QueueClient, QueuePublisher, QueueStatistics, QueueSubscriber, Serializer, UnsealedQueueClient}
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest

import java.net.URI

private class SQSClient[F[_]](client: SqsAsyncClient)(implicit F: Async[F]) extends UnsealedQueueClient[F] {

  private def getQueueUrl(name: String): F[String] =
    F.fromCompletableFuture {
      F.delay {
        client.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build())
      }
    }.map(_.queueUrl)
      .adaptError(makeQueueException(_, name))

  override def administration: QueueAdministration[F] =
    new SQSAdministration(client, getQueueUrl(_))

  override def statistics(name: String): QueueStatistics[F] =
    new SQSStatistics(name, client, getQueueUrl(name))

  override def publish[T: Serializer](name: String): QueuePublisher[F, T] =
    new SQSPublisher(name, client, getQueueUrl(name))

  override def subscribe[T: Deserializer](name: String): QueueSubscriber[F, T] =
    new SQSSubscriber[F, T](name, client, getQueueUrl(name))

}

object SQSClient {

  /**
   * Creates an SQS client for the given `region` and `credentials`.
   *
   * If `httpClient` is provided, it is not shut down when the resulting resource is released.
   * It is up to the caller to ensure that the client is properly managed.
   * This is useful if you integrate the library in an existing code base which already uses a client.
   */
  def apply[F[_]](
    region: Region,
    credentials: AwsCredentialsProvider,
    endpoint: Option[URI] = None,
    httpClient: Option[SdkAsyncHttpClient] = None
  )(implicit F: Async[F]
  ): Resource[F, QueueClient[F]] =
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

  /**
   * Creates an SQS client from a given instance of `SqsAsyncClient`.
   *
   * It is upon the caller to ensure closing of the underlying client once appropriate.
   */
  def fromClient[F[_]](client: SqsAsyncClient)(implicit F: Async[F]): QueueClient[F] =
    new SQSClient(client)

}
