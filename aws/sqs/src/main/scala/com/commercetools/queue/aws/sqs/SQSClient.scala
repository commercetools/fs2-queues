package com.commercetools.queue.aws.sqs

import cats.effect.{IO, Resource}
import com.commercetools.queue.{Deserializer, QueueAdministration, QueueClient, QueuePublisher, QueueSubscriber, Serializer}
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest

import java.net.URI

class SQSClient private (client: SqsAsyncClient) extends QueueClient {

  private def getQueueUrl(name: String): IO[String] =
    IO.fromCompletableFuture {
      IO {
        client.getQueueUrl(GetQueueUrlRequest.builder().queueName(name).build())
      }
    }.map(_.queueUrl)

  override def administration: QueueAdministration =
    new SQSAdministration(client, getQueueUrl(_))

  override def publisher[T: Serializer](name: String): Resource[IO, QueuePublisher[T]] =
    Resource.eval(getQueueUrl(name).map(new SQSPublisher(_, client)))

  override def subscriber[T: Deserializer](name: String): QueueSubscriber[T] =
    new SQSSubscriber[T](getQueueUrl(name), client)

}

object SQSClient {

  def apply(
    region: Region,
    credentials: AwsCredentialsProvider,
    endpoint: Option[URI] = None,
    httpClient: Option[SdkAsyncHttpClient] = None
  ): Resource[IO, SQSClient] =
    Resource
      .fromAutoCloseable {
        IO {
          val builder =
            SqsAsyncClient.builder().region(region).credentialsProvider(credentials)

          endpoint.foreach(builder.endpointOverride(_))

          httpClient.foreach(builder.httpClient(_))

          builder.build()
        }
      }
      .map(new SQSClient(_))

}
