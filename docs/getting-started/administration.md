{% nav = true %}
# Managing Queues

Managing queues is made using the @:api(com.commercetools.queue.QueueAdministration). You can acquire an instance through a @:api(com.commercetools.queue.QueueClient) by using the `administration()` method. A `QueueAdministration` instance is **not** associated to any specific queue. The queue to operate on will be provided in each administration method.

```scala mdoc
import cats.effect.IO

import com.commercetools.queue.{QueueAdministration, QueueClient}

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

admin.create("my-queue", messageTTL = 14.days, lockTTL = 2.minutes)
```

@:callout(info)
The `lockTTL` parameter has an influence on the behavior of the [subscriber][doc-subscribing] methods and streams. If you change it at runtime, you should restart the subscribing streams to ensure they are taken into account.
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

[doc-subscribing]: subscribing.md
