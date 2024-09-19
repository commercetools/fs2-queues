{% nav = true %}
# Publishing Data

Publishing data is made using a @:api(QueuePublisher). You can acquire one through a @:api(QueueClient) by using the `publish()` method. A `QueuePublisher` is associated to a specific queue, which is provided when creating the publisher.

The publisher also requires a [data serializer][doc-serializer] upon creation for the type of data you want to publish to it.

```scala mdoc
import cats.effect.IO

import com.commercetools.queue.{QueueClient, QueuePublisher}

def client: QueueClient[IO] = ???

// returns a publisher for the queue named `my-queue`,
// which can publish messages of type String
def publisher: QueuePublisher[IO, String] =
  client.publish[String]("my-queue")
```

## Pipe a stream through the publisher sink

The @:api(QueuePublisher) abstraction provides a `sink()` pipe, through which you can make your publishing source stream go.
The pipe takes a parameter allowing for batching publications.

```scala mdoc:compile-only
import fs2.{Pipe, Stream}

val input: Stream[IO, (String, Map[String, String])] = ???

// messages are published in batch of 10
val publicationSink: Pipe[IO, (String, Map[String, String]), Nothing] = publisher.sink(batchSize = 10)

// pipe the message producing stream through the publication sink
input.through(publicationSink)
```

@:callout(info)
Several `Stream`s can safely publish to the same sink concurrently, so you can reuse the `publicationSink` variable.
@:@

## Explicit publish

If you are integrating the library with an existing code base that performs explicit publications to the queue, you can access the @:api(QueuePusher) lower level API, which exposes ways to publish a single message or a single batch.
This abstraction comes in handy when the messages you produce do not come from a `Stream`, otherwise you should prefer the `sink()` pipe presented above.

A `QueuePusher` is accessed as a [`Resource`][cats-effect-resource] as it usually implies using a connection pool. When the resource is released, the pools will be disposed properly.

```scala mdoc:compile-only
publisher.pusher.use { queuePusher =>
  val produceMessages: IO[List[(String, Map[String, String])]] = ???

  // produce a batch
  produceMessages
    .flatMap { messages =>
      // push the batch
      queuePusher.push(messages, None)
    }
    // repeat forever
    .foreverM
}
```

@:callout(warning)
Make sure that `IO`s publishing to the `queuePusher` do not outlive the `use` scope, otherwise you will be using a closed resource after the `use` block returns.
If you need to spawn background fibers using the `queuePusher`, you can for instance use a [`Supervisor`][cats-effect-supervisor] whose lifetime is nested within the `queuePusher` one.

```scala mdoc:compile-only
import cats.effect.std.Supervisor

publisher.pusher.use { queuePusher =>
  val produceMessages: IO[List[(String, Map[String, String])]] = ???

  // create a supervisor that waits for supervised spawn fibers
  // to finish before being released
  Supervisor[IO](await = true).use { supervisor =>
    // produce a batch
    produceMessages
      .flatMap { messages =>
        // push the batch in the background
        supervisor.supervise(queuePusher.push(messages, None)).void
      }
  }
}
```
@:@

[cats-effect-resource]: https://typelevel.org/cats-effect/docs/std/resource
[cats-effect-supervisor]: https://typelevel.org/cats-effect/docs/std/supervisor
[doc-serializer]: serialization.md#data-serializer
