package com.commercetools.queue.gcp.pubsub

import cats.effect.{Async, Resource}
import com.commercetools.queue.{QueuePublisher, QueuePusher, Serializer}
import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.rpc.TransportChannelProvider
import com.google.cloud.pubsub.v1.stub.{HttpJsonPublisherStub, PublisherStubSettings}
import com.google.pubsub.v1.TopicName

class PubSubPublisher[F[_], T](
  val queueName: String,
  topicName: TopicName,
  channelProvider: TransportChannelProvider,
  credentials: CredentialsProvider,
  endpoint: Option[String]
)(implicit
  F: Async[F],
  serializer: Serializer[T])
  extends QueuePublisher[F, T] {

  override def pusher: Resource[F, QueuePusher[F, T]] =
    Resource
      .fromAutoCloseable {
        F.blocking {
          val builder =
            PublisherStubSettings
              .newHttpJsonBuilder()
              .setCredentialsProvider(credentials)
              .setTransportChannelProvider(channelProvider)
          endpoint.foreach(builder.setEndpoint(_))
          HttpJsonPublisherStub.create(builder.build())

        }
      }
      .map(new PubSubPusher[F, T](queueName, topicName, _))

}
