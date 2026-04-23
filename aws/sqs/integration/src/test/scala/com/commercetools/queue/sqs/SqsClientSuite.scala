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

package com.commercetools.queue.sqs

import cats.effect.{IO, Resource}
import cats.syntax.all._
import com.commercetools.queue.QueueClient
import com.commercetools.queue.aws.sqs.{SQSClient, SQSConfig}
import com.commercetools.queue.testkit.QueueClientSuite
import software.amazon.awssdk.auth.credentials.{AnonymousCredentialsProvider, AwsCredentialsProvider, AwsSessionCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{GetQueueUrlRequest, ListQueueTagsRequest}

import java.net.URI
import scala.jdk.CollectionConverters._

class SqsClientSuite extends QueueClientSuite {

  private val testTags: Map[String, String] = Map("project" -> "fs2-queues", "env" -> "test")

  private def config: IO[(Region, AwsCredentialsProvider, Option[URI])] =
    booleanOrDefault("AWS_SQS_USE_EMULATOR", default = true).ifM(
      ifTrue =
        IO.pure((Region.EU_WEST_1, AnonymousCredentialsProvider.create(), Some(new URI("http://localhost:4566")))),
      ifFalse = for {
        awsRegion <- string("AWS_SQS_REGION")
        region <- Region
          .regions()
          .asScala
          .find(_.id == awsRegion)
          .liftTo[IO](new IllegalArgumentException(s"Cannot find any suitable AWS region from $awsRegion value!"))
        accessKey <- string("AWS_SQS_ACCESS_KEY")
        accessSecret <- string("AWS_SQS_ACCESS_SECRET")
        sessionToken <- string("AWS_SQS_SESSION_TOKEN")
        credentials <- IO.pure(
          StaticCredentialsProvider.create(
            AwsSessionCredentials.create(accessKey, accessSecret, sessionToken)
          ))
      } yield (region, credentials, None)
    )

  override def client: Resource[IO, QueueClient[IO]] =
    config.toResource.flatMap { case (region, credentials, endpoint) =>
      SQSClient[IO](region, credentials, endpoint = endpoint)
    }
  override def clientWithTags: Resource[IO, QueueClient[IO]] =
    config.toResource.flatMap { case (region, credentials, endpoint) =>
      SQSClient[IO](region, credentials, endpoint = endpoint, config = SQSConfig(testTags))
    }

  private def assertQueueTags(queueName: String, expectedTags: Map[String, String]): IO[Unit] =
    config.flatMap { case (region, credentials, endpoint) =>
      Resource
        .fromAutoCloseable(IO.delay {
          val builder = SqsAsyncClient.builder().region(region).credentialsProvider(credentials)
          endpoint.foreach(builder.endpointOverride(_))
          builder.build()
        })
        .use { rawClient =>
          for {
            urlResp <- IO.fromCompletableFuture(
              IO.delay(rawClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build())))
            tagsResp <- IO.fromCompletableFuture(
              IO.delay(rawClient.listQueueTags(ListQueueTagsRequest.builder().queueUrl(urlResp.queueUrl()).build())))
            _ <- IO(assertEquals(tagsResp.tags().asScala.toMap, expectedTags))
          } yield ()
        }
    }

  withQueueWithTags.test("queue should have the configured tags on creation") { queueName =>
    assertQueueTags(queueName, testTags)
  }

  withQueue.test("queue should have the configured tags after update") { queueName =>
    clientWithTags
      .use(
        _.administration.update(queueName, None, None) >>
          assertQueueTags(queueName, testTags)
      )
  }

}
