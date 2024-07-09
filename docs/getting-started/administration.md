{% nav = true %}
# Managing Queues

Managing queues is made using the @:api(com.commercetools.queue.QueueAdministration). You can acquire an instance through a @:api(com.commercetools.queue.QueueClient) by using the `administration()` method. A `QueueAdministration` instance is **not** associated to any specific queue. The queue to operate on will be provided in each administration method.

```scala mdoc
import cats.effect.IO

import com.commercetools.queue.{QueueAdministration, QueueClient, QueueCreationConfiguration}

def client: QueueClient[IO] = ???

def admin: QueueAdministration[IO] =
  client.administration
```

## Create a queue

A queue can be created by using the `create()` method.
You need to provide the message TTL and lock TTL together with the new queue name.
The different TTLs are defined at queue level and will affect messages published and subscribers.

```scala mdoc:compile-only
import scala.concurrent.duration._

admin.create("my-queue", QueueCreationConfiguration(messageTTL = 14.days, lockTTL = 2.minutes))
```

@:callout(info)
The `lockTTL` parameter has an influence on the behavior of the [subscriber][doc-subscribing] methods and streams. If you change it at runtime, you should restart the subscribing streams to ensure they are taken into account.
@:@

### Dead-letter queues

When creating a new queue, there is a possibility to pass a `deadletter` parameter to the `QueueCreationConfiguration` instance. This will make the library bind the newly created queue with a dead letter queue, and configure the number of delivery attempts after which a message is put in the dead letter queue, as configured.

```scala mdoc:compile-only
import com.commercetools.queue.DeadletterQueueCreationConfiguration

import scala.concurrent.duration._

admin.create("my-queue",
             QueueCreationConfiguration(
               messageTTL = 14.days,
               lockTTL = 2.minutes,
               Some(DeadletterQueueCreationConfiguration(maxAttempts = 100))))
```

@:callout(info)
Some queue providers might always create a dead letter queue when a queue is created (e.g. on Azure Service Bus). In this case, if no configuration is provided explicitly the default values will be used.

Please refer to the queue provider documentation for more information.
@:@

## Check if queue exists

You can check the existence of a queue in the queue system by calling the `exists()` method.

```scala mdoc:compile-only
admin.exists("my-queue")
```

## Update queue properties

The queue TTLs can be updated for an existing queue by using the `update()` method. The method accepts both TTLs as `Option[FiniteDuration]` with a default value of `None`. Only the defined TTLs will be updated. Values that are not provided are left unchanged.

```scala mdoc:compile-only
import scala.concurrent.duration._

// only updates the lock TTL
admin.update("my-queue", lockTTL = Some(5.minutes))

// updates both the message and lock TTLs
admin.update("my-queue", messageTTL = Some(1.day), lockTTL = Some(5.minutes))
```

## Delete a queue

You can delete an existing queue by calling the `delete()` method.

```scala mdoc:compile-only
admin.delete("my-queue")
```

@:callout(info)
If the library detects that a dead letter queue was configured, it tries to delete it as well.
@:@

[doc-subscribing]: subscribing.md
