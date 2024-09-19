{% nav = true %}
# Testing

The testing module provides tools to write unit tests for code base using the `fs2-queues` library.

```scala
libraryDependencies += "com.commercetools" %% "fs2-queues-testing" % "@VERSION@"
```

## Using `TestQueue`

The @:api(testing.TestQueue) class implements an in-memory queue system. A `TestQueue` can be wrapped to create a:

  - puller via @:api(testing.TestQueuePuller$) `apply` method
  - pusher via @:api(testing.TestQueuePusher$) `apply` method
  - subscriber via @:api(testing.TestQueueSubscriber$) `apply` method
  - publisher via @:api(testing.TestQueuePublisher$) `apply` method

The `TestQueue` and the various test tools are designed to work well when used with the [cats-effect test runtime][test-runtime]

For instance, if you want to test code that needs to publish with delays, you can use the following approach:

```scala mdoc
import com.commercetools.queue.testing._

import scala.concurrent.duration._

import cats.effect._
import cats.effect.testkit._
import cats.effect.unsafe.implicits.global

TestQueue[String](name = "test-queue", messageTTL = 10.minutes, lockTTL = 1.minute)
  .flatMap { testQueue =>
    val puller = TestQueuePuller(testQueue)  
    val pusher = TestQueuePusher(testQueue)  

    val program =
      for {
        _ <- pusher.push("one", Map.empty, Some(10.seconds))
        immediatelyPulled <- puller.pullBatch(10, Duration.Zero)
        _ <- IO.sleep(11.seconds)
        laterPulled <- puller.pullBatch(10, Duration.Zero)
      } yield (immediatelyPulled.size, laterPulled.size)

    TestControl.executeEmbed(program)
  }
  .unsafeRunSync()
```

## Using custom effects

You might want to use a custom effect in your unit test, instead of a full blown queue implementation. For instance, if you want to test the behavior of a failing push, you can use the following approach:

```scala mdoc:crash
val pusher = TestQueuePusher.fromPush[String]((_, _, _) => IO.raiseError(new Exception("BOOM!")))

pusher.push("test message", Map.empty, None).unsafeRunSync()
```

The provided function takes the same parameters as the `QueuePusher.push` method for a single message and can return any effect.

These variants are available on test entities.

## Testing message contexts

If you need to unit test different behavior on message contexts, you can use the @:api(testing.TestingMessageContext) class.
It allows you to create a message context with different behaviors.

For instance, if you want to check the behavior of a failing ack on a message, you can use this approach:


```scala mdoc:crash
val ctx = TestingMessageContext("payload").failing(new Exception("BOOM!"))

ctx.ack().unsafeRunSync()
```

[test-runtime]: https://typelevel.org/cats-effect/docs/core/test-runtime
