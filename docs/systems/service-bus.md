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

[service-bus]: https://learn.microsoft.com/en-us/azure/service-bus-messaging/service-bus-messaging-overview
