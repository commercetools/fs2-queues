{% nav = true %}
# Receiving Data

Receiving data is achieved through a @:api(QueueSubscriber). You can acquire one throuh a @:api(QueueClient) by using the `subscribe()` method. A `QueueSubscriber` is associated with a specific queue, which is provided when creating the subscriber.

The subscriber also requires a [data deserializer][doc-deserializer] upon creation, to deserialize the message payload received from the queue.

```scala mdoc
import cats.effect.IO
import cats.syntax.all._
import scala.concurrent.duration._
import com.commercetools.queue.{Decision, Message, QueueClient, QueueSubscriber}
import com.commercetools.queue.Decision._

def client: QueueClient[IO] = ???

def doSomething(message: Message[IO, String]): IO[Unit] = IO.unit

val queueName: String = "my-queue"

// returns a subscriber for the queue named `my-queue`,
// which can receive messages of type String
def subscriber: QueueSubscriber[IO, String] =
  client.subscribe[String](queueName)
```

When a message is received by a subscriber, it is _locked_ (or _leased_) for a queue level configured amount of time (see more on the [Managing Queues] page). This means that only one subscriber receives (and can process) a given message in this amount of time. A message needs to be settled once processed (or if processing fails) to ensure it is either removed from the queue or made available again. This is part of the message control flow.

In the following, we explain what kind of control flow handling is provided by the library.

## Processors

The @:api(QueueSubscriber) abstraction provides a `processWithAutoAck()` method, which automatically handles the control flow part for you. You only need to provide the processing function, allowing you to focus on your business logic.

@:callout(info)
The `payload` is effectful as it performs data deserialization, which can fail when a message payload is malformed.
You can access the raw data by using `rawPayload`.

The deserialized data is memoized so that subsequent accesses to it are not recomputed.
@:@

```scala mdoc:compile-only
subscriber.processWithAutoAck(batchSize = 10, waitingTime = 20.seconds) { message =>
  message.payload.flatMap { payload =>
    IO.println(s"Received $payload").as(message.messageId)
  }
}
```

The processing function receives a @:api(Message), which gives access to the content and some metadata of the received messages.

The result is a `Stream` of the processing results, emitted in the order the messages where received. Only the successfully processed messages are emitted down-stream. The stream is failed upon the first failed processing.

The `processWithAutoAck` method performs automatic acking/nacking for you depending on the processing outcome. It comes in handy to implement at least once delivery startegies, releasing the message to be reprocessed by another subscriber in case of error.

If you wish to implement a stream that does not fail upon error, you can use the `attemptProcessWithAutoAck()` methods, which emits the results of the processing as an `Either[Throwable, T]`. The resulting stream does not fail if some processing fails. Otherwise it has the same behavior as the stream above.

For more flexibility in terms of what to do with each received messages, you can also check `process()` and `processWithImmediateDecision()`, 
that will give access to the content and some metadata of the received messages, apply some effects and return a @:api(Decision), 
to dictate whether each message should be confirmed (see @:api(Decision.Ok)), dropped (see @:api(Decision.Drop), 
considered as failed (see @:api(Decision.Fail)), or if the message should be re-enqueued (see @:api(Decision.Reenqueue)).
An @:api(ImmediateDecision) is a kind of decision that won't allow messages to get re-enqueued.

These variants of processors can be as involved as needed, and allow to cover a wide range of use cases, declaratively.

```scala mdoc:compile-only
subscriber.process[Int](
  batchSize = 5,
  waitingTime = 1.second,
  publisherForReenqueue = client.publish(queueName))(
  (msg: Message[IO, String]) =>
    (for {
      payload <- msg.payload
      i <- payload.toIntOption.liftTo[IO](new Exception("Payload is not an integer"))
      res <- i match {
        // coercing to Int, as an example to show the options
        // Checking various scenarios, like a message that gets reenqueue'ed once and then ok'ed,
        // a message dropped, a message failed and ack'ed, a message failed and not ack'ed.
        // A business logic would do something with the message, effectfully, and dictate what to do with that at the end.
        case 0 => doSomething(msg).as(Decision.Ok(0))
        case 1 if msg.metadata.contains("reenqueued") => doSomething(msg).as(Decision.Ok(1))
        case 1 => doSomething(msg).as(Decision.Reenqueue(Map("reenqueued" -> "true").some, None))
        case 2 => doSomething(msg).as(Decision.Drop)
        case 3 => doSomething(msg).as(Decision.Fail(new Throwable("3"), ack = true))
        case 4 => doSomething(msg).as(Decision.Fail(new Throwable("4"), ack = false))
      }
    } yield res)
    .handleErrorWith { t =>
      // do some logging for instance
      IO.pure(Decision.Fail(t, ack = true))
    }
  )
```

## Raw message stream

If you want more fine-grained tuning of the control flow part, you can resort to the `messages()` stream available via the `QueueSubscriber`.
The stream emits a @:api(MessageContext) for each received message, giving access to the message content and metadata, as well as to message control methods.

Using this stream, you can implement your processing strategy.

@:callout(warning)
Using this stream, you need to explicitly settle the message (either by acking or nacking it) once processed. Failing to do so, results in the message being made available to other subscribers once the lock expires.

It is up to you to ensure that all control flow paths lead to explicit settlement of the messages you received.

The recommendation is to only resort to this stream if you need to implement complex custom control flow for messages.
@:@

A simplified `processWithAutoAck` method described above could be implemented this way.

@:callout(info)
The real implementation is more complex to ensure that all successfully processed messages are actually emitted down-stream and is more efficient.
@:@

```scala mdoc:compile-only
import cats.effect.Outcome

import scala.concurrent.duration._

subscriber
  .messages(batchSize = 10, waitingTime = 20.seconds)
  .evalMap { context =>
    context.payload.flatMap { payload =>
      IO.println(s"Received $payload")
        .as(context.messageId)
    }
    .guaranteeCase {
      case Outcome.Succeeded(_) => context.ack()
      case _ => context.nack()
    }
  }
```

For high throughput scenarios where acknowledging individual messages wouldn't be optimal, consider using `messageBatches()`.

Batching method exposes `MessageBatch` giving user control over entire batch as a whole allowing for batched acknowledgement if the implementation supports it.

Chunked messages can be accessed via `messages`.

```scala mdoc:compile-only
import cats.effect.Outcome

subscriber
  .messageBatches(batchSize = 10, waitingTime = 20.seconds)
  .evalMap { batch =>
    batch.messages.parTraverse_ { msg =>
      msg.payload.flatTap { payload => 
        IO.println(s"Received $payload")
      }
    }.guaranteeCase {
      case Outcome.Succeeded(_) => batch.ackAll
      case _ => batch.nackAll
    }
  }
```


### `MessageContext` control flow

There are three different methods that can be used to control the message lifecycle from the subscriber point of view:

 1. `MessageContext.ack()` acknowledges the message, and marks it as successfully processed in the queue system. It will be permanently removed and no other subscriber will ever receive it.
 2. `MessageContext.nack()` marks the message as not processed, releasing the lock in the queue system. It will be viewable for other subscribers to receive and process.
 3. `MessageContext.extendLock()` extends the currently owned lock by the queue level configured duration. This can be called as many times as you want, as long as you still own the lock. As long as the lock is extended, the message will not be distributed to any other subscriber by the queue system.

### `MessageBatch` control flow

Methods have the same semantics to `MessageContext` ones with the difference that they act on all messages from the batch at once. Whether the action is atomic across all messages depends on the underlying implementation.

1. `MessageContext.ackAll()` acknowledges all the messages from the batch.
2. `MessageContext.nackAll()` marks all the messages from the batch as not processed.


## Explicit pull

If you are integrating this library with an existing code base that performs explicit pulls from the queue, you can access the @:api(QueuePuller) lower level API, which exposes ways to pull batch of messages.
This abstraction comes in handy when your processing code is based on a callback approach and is not implemented as a `Stream`, otherwise you should prefer the streams presented above.

A `QueuePuller` is accessed as a [`Resource`][cats-effect-resource] as it usually implies using a connection pool. When the resource is released, the pools will be disposed properly.

```scala mdoc:compile-only
import cats.effect.Outcome

import scala.concurrent.duration._

import cats.syntax.foldable._

subscriber.puller.use { queuePuller =>

  queuePuller
    .pullBatch(batchSize = 10, waitingTime = 20.seconds)
    .flatMap { chunk =>
      chunk.traverse_ { context =>
        context.payload.flatMap { payload =>
          IO.println(s"Received $payload")
        }.guaranteeCase {
          case Outcome.Succeeded(_) => context.ack()
          case _ => context.nack()
        }
      }
    }
}
```

@:callout(warning)
Make sure that `IO`s pulling from the `queuePuller` do not outlive the `use` scope, otherwise you will be using a closed resource after the `use` block returns.
If you need to spawn background fibers using the `queuePuller`, you can for instance use a [`Supervisor`][cats-effect-supervisor] whose lifetime is nested within the `queuePuller` one.

```scala mdoc:compile-only
import cats.effect.Outcome
import cats.effect.std.Supervisor

import scala.concurrent.duration._

import cats.syntax.foldable._

subscriber.puller.use { queuePuller =>
  // create a supervisor that waits for supervised spawn fibers
  // to finish before being released
  Supervisor[IO](await = true).use { supervisor =>
    queuePuller
      .pullBatch(batchSize = 10, waitingTime = 20.seconds)
      .flatMap { chunk =>
        chunk.traverse_ { context =>
          supervisor.supervise {
            context.payload.flatMap { payload =>
              IO.println(s"Received $payload")
            }
            .guaranteeCase {
              case Outcome.Succeeded(_) => context.ack()
              case _ => context.nack()
            }
          }
        }
      }
  }
}
```
@:@

To pull batches that can be acknowledged in batches, use `pullMessageBatch()`

```scala mdoc:compile-only
import cats.effect.Outcome

subscriber.puller.use { queuePuller =>

  queuePuller
    .pullMessageBatch(batchSize = 10, waitingTime = 20.seconds)
    .flatMap { batch =>
      batch.messages.traverse_ { message =>
        message.payload.flatMap { payload =>
          IO.println(s"Received $payload")
        }.guaranteeCase {
          case Outcome.Succeeded(_) => batch.ackAll
          case _ => batch.nackAll
        }
      }
    }
  
}
```

[cats-effect-resource]: https://typelevel.org/cats-effect/docs/std/resource
[cats-effect-supervisor]: https://typelevel.org/cats-effect/docs/std/supervisor
[doc-deserializer]: serialization.md#data-deserializer
