# Azure Service Bus Queues

You can create a client to service bus queues by using the [Azure Service Bus][service-bus] module.

```scala
libraryDependencies += "com.commercetools" %% "fs2-queues-azure-service-bus" % "@VERSION@"
```

For instance, you can create a managed client via a namespace and credentials as follows.

```scala mdoc:compile-only
import cats.effect.IO
import com.commercetools.queue.azure.servicebus._
import com.azure.identity.DefaultAzureCredentialBuilder

val namespace = "{namespace}.servicebus.windows.net" // your namespace
val credentials = new DefaultAzureCredentialBuilder().build() // however you want to authenticate

ServiceBusClient[IO](namespace, credentials).use { client =>
  ???
}
```

The client is managed, meaning that it uses a conection pool that will get shut down upon resource release.

If integrating with an existing code base where you already have builders that you would like to share, you can use the `unmanaged` variant.

## Global setting for queue creation

When queues are created using the library, you can define global settings at client instantiation time. These settings will be applied to any queue created with the client instance.

For instance, this comes in handy when creating queues in a partitioned Premium namespace, you need to indicate that the queue is partitioned.

```scala mdoc:compile-only
import cats.effect.IO
import com.commercetools.queue.azure.servicebus._
import com.azure.identity.DefaultAzureCredentialBuilder

import scala.concurrent.duration._

val namespace = "{namespace}.servicebus.windows.net" // your namespace
val credentials = new DefaultAzureCredentialBuilder().build() // however you want to authenticate

// queues will be partitioned and have a size of 20GiB
// message size is not changed
val globalQueueSettings =
  NewQueueSettings.default.copy(
    partitioned = Some(true),
    queueSize = Some(Size.gib(20)))

ServiceBusClient[IO](namespace, credentials, newQueueSettings = globalQueueSettings).use { client =>
  // the queue will be partitioned and will be able to store up to 20GiB of data
  client.administration.create("my-queue", messageTTL = 1.hour, lockTTL = 10.seconds)
}
```

[service-bus]: https://learn.microsoft.com/en-us/azure/service-bus-messaging/service-bus-messaging-overview
