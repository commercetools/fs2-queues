# GCP PubSub

You can create a client to service bus queues by using the [GCP PubSub][pubsub] module.

```scala
libraryDependencies += "com.commercetools" %% "fs2-queues-gcp-pubsub" % "@VERSION@"
```

For instance you can create a managed client via a region and credentials as follows.

```scala mdoc:compile-only
import cats.effect.IO
import com.commercetools.queue.gcp.pubsub._
import com.google.api.gax.core.GoogleCredentialsProvider

val project = "my-project" // your project
val credentials = GoogleCredentialsProvider.newBuilder().build() // however you want to authenticate
val configs = PubSubConfig(subscriptionNamePrefix="us-central1-") // Prefix for the subscription prefix. The prefix should contain also the desired separator

PubSubClient[IO]( project= project, credentials= credentials,configs= configs).use { client =>
  ???
}
```

The client is managed, meaning that it uses a dedicated HTTP connection pool that will get shut down upon resource release.
The client accepts a `PubSubConfig` object that allows you to configure the prefix for the subscription name. if not provided it will use the default prefix `fs2-queues-` to avoid collisions.

If integrating with an existing code base where you already have an instance of `TransportChannelProvider` that you would like to share, you can use the `unmanaged` construtor.
In this case, it is up to you to manage the channel provider life cycle.

[pubsub]: https://cloud.google.com/pubsub/
