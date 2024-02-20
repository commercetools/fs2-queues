# Common Cloud Client Tools

Aims at providing a unified way of working with cloud queues (SQS, PubSub, Service Bus, ...) across all CT scala services.

## Common queue interface

The library offers both low and high level possibilities, making it possible to have fine grained control over queue pulling, or just focusing on processing, delegating message management to the library.

The design of the API is the result of the common usage patterns at CT and how the various client SDKs are designed.
There are several views possible on a queue:
 - as a `QueuePublisher` when you only need to publish messages to an existing queue.
 - as a `QueueSubscriber` when you only need to subscribe to an existing queue.
 - as a `QueueAdministration` when you need to manage queues (creation, deletion, ...).

The entry point is the `QueueClient` factory for each underlying queue system.

In the examples below, we will use the following publisher and subscriber streams:

```scala mdoc
import fs2.Stream
import cats.effect.IO
import cats.effect.std.Random
import scala.concurrent.duration._

import com.commercetools.queue._

def publishStream(publisher: QueuePublisher[String]): Stream[IO, Nothing] =
  Stream.eval(Random.scalaUtilRandom[IO]).flatMap { random =>
    Stream
     // repeatedly emit a random string of length 10
     .repeatEval(random.nextString(10))
     // every 100 milliseconds
     .metered(100.millis)
     // and publish in batch of 10
     .through(publisher.sink(batchSize = 10))
  }

def subscribeStream(subscriber: QueueSubscriber[String]): Stream[IO, Nothing] =
  subscriber
    // receives messages in batches of 5,
    // waiting max for 20 seconds
    // print every received message,
    // and ack automatically
    .processWithAutoAck(5, 20.seconds)(msg => IO.println(msg.payload))
    // results are non important
    .drain

def program(client: QueueClient): IO[Unit] = {
  val queueName = "my-queue"
  client.publisher[String](queueName).use { publisher =>
    // subscribe and publish concurrently
    subscribeStream(client.subscriber[String](queueName))
      .concurrently(publishStream(publisher))
      .compile
      // runs forever
      .drain
  }
}
```

## Working with Azure Service Bus queues

```scala mdoc:compile-only
import com.commercetools.queue.azure.servicebus._
import com.azure.identity.DefaultAzureCredentialBuilder

val namespace = "{namespace}.servicebus.windows.net" // your namespace
val credentials = new DefaultAzureCredentialBuilder().build() // however you want to authenticate

ServiceBusClient(namespace, credentials).use(program(_))
```

## Working with AWS SQS


```scala mdoc:compile-only
import com.commercetools.queue.aws.sqs._
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider

val region = Region.US_EAST_1 // your region
val credentials = DefaultCredentialsProvider.create() // however you want to authenticate

SQSClient(region, credentials).use(program(_))
```
