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
import com.commercetools.queue.aws.sqs.SQSClient
import com.commercetools.queue.testkit.QueueClientSuite
import software.amazon.awssdk.auth.credentials.{AnonymousCredentialsProvider, AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region

import java.net.URI
import scala.jdk.CollectionConverters.CollectionHasAsScala

class SqsClientSuite extends QueueClientSuite {

  private def config =
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
        credentials <- (string("AWS_SQS_ACCESS_KEY"), string("AWS_SQS_ACCESS_SECRET")).mapN((accessKey, accessSecret) =>
          StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, accessSecret)
          ))
      } yield (region, credentials, None)
    )

  override def client: Resource[IO, QueueClient[IO]] =
    config.toResource.flatMap { case (region, credentials, endpoint) =>
      SQSClient[IO](
        region,
        credentials,
        endpoint = endpoint
      )
    }

}
