package com.commercetools.queue.gcp.pubsub

import cats.effect.{Async, Resource}
import com.commercetools.queue.{Deserializer, QueueAdministration, QueueClient, QueuePublisher, QueueSubscriber, Serializer}
import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.httpjson.{HttpJsonTransportChannel, ManagedHttpJsonChannel}
import com.google.api.gax.rpc.{FixedTransportChannelProvider, TransportChannel, TransportChannelProvider}
import com.google.pubsub.v1.{SubscriptionName, TopicName}

class PubSubClient[F[_]: Async] private (
  project: String,
  channelProvider: TransportChannelProvider,
  credentials: CredentialsProvider,
  endpoint: Option[String])
  extends QueueClient[F] {

  override def administration: QueueAdministration[F] =
    new PubSubAdministration[F](project, channelProvider, credentials, endpoint)

  override def publish[T: Serializer](name: String): QueuePublisher[F, T] =
    new PubSubPublisher[F, T](name, TopicName.of(project, name), channelProvider, credentials, endpoint)

  override def subscribe[T: Deserializer](name: String): QueueSubscriber[F, T] =
    new PubSubSubscriber[F, T](
      name,
      SubscriptionName.of(project, s"fs2-queue-$name"),
      channelProvider,
      credentials,
      endpoint)

}

object PubSubClient {

  private def makeDefaultTransportChannel(endpoint: Option[String]): TransportChannel =
    HttpJsonTransportChannel.create(
      ManagedHttpJsonChannel.newBuilder().setEndpoint(endpoint.getOrElse("https://pubsub.googleapis.com")).build())

  def apply[F[_]](
    project: String,
    credentials: CredentialsProvider,
    endpoint: Option[String] = None,
    mkTransportChannel: Option[String] => TransportChannel = makeDefaultTransportChannel _
  )(implicit F: Async[F]
  ): Resource[F, PubSubClient[F]] =
    Resource
      .fromAutoCloseable(F.blocking(mkTransportChannel(endpoint)))
      .map { channel =>
        new PubSubClient[F](project, FixedTransportChannelProvider.create(channel), credentials, endpoint)
      }

}
