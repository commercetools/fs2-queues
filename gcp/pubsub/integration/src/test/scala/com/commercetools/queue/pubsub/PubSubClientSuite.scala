package com.commercetools.queue.pubsub

import cats.effect.{IO, Resource}
import com.commercetools.queue.QueueClient
import com.commercetools.queue.gcp.pubsub.PubSubClient
import com.commercetools.queue.testkit.QueueClientSuite
import com.google.api.gax.core.NoCredentialsProvider

class PubSubClientSuite extends QueueClientSuite {

  override def client: Resource[IO, QueueClient[IO]] =
    PubSubClient("test-project", NoCredentialsProvider.create(), endpoint = Some("http://localhost:8042"))

}
