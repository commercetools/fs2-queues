{% nav = true %}
# Queue Statistics

The library exposes a simple interface to access basic queue statistics through a @:api(QueueStatistics).

@:callout(warning)
The numbers reported by the statistics are approximate numbers. Depending on the underlying system, there might be some delay in data availability.

When in doubt, refer to the queue provider documentation.
@:@

```scala mdoc
import cats.effect.IO

import com.commercetools.queue.{QueueClient, QueueStatistics}

def client: QueueClient[IO] = ???

// returns a statistics accessor for the queue named `my-queue`
def stats: QueueStatistics[IO] =
  client.statistics("my-queue")
```

## Building a stream of statistics

This interface expoes a way to build a stream of statistics, polling them at some configured interval. For instance, this code will print the statistics polled every 20 seconds.

```scala mdoc:compile-only
import scala.concurrent.duration._

stats
  .stream(interval = 20.seconds)
  .evalMap { 
    case Right(stats) => IO.println(stats)
    case Left(t) => IO.println(s"Statistics failed to be retrieved: ${t.getMessage()}")
  }
  .drain
```

If you want the stream to fail upon the first fetching error, you can use the `strictStream` variant.

## Explicit fetch

If you are integrating this library with an existing code base that performs explicit fetches for queue statistics, you can access the @:api(QueueStatsFetcher) lower level API, which exposes a way to fetch statistics explicitly.

A `QueueStatsFetcher` is accessed as a [`Resource`][cats-effect-resource] as it usually implies using a connection pool. When the resource is released, the pools will be disposed properly.

The explicitly fetch and report statistics every 20 seconds, one can use this approach (errors are not properly handled, and the stream approach above should be preferred):

```scala mdoc:compile-only
import scala.concurrent.duration._

stats.fetcher.use { statsFetcher =>
  (statsFetcher
    .fetch
    .flatMap(IO.println(_)) *>
   IO.sleep(20.seconds)).foreverM
}
```

[cats-effect-resource]: https://typelevel.org/cats-effect/docs/std/resource
