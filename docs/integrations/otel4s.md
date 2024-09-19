# Otel4s

The otel4s provides an integration with the [otel4s][otel4s] library.

```scala
libraryDependencies += "com.commercetools" %% "fs2-queues-otel4s" % "@VERSION@"
```

It allows you to wrap an existing @:api(QueueClient) into a @:api(otel4s.MeasuringQueueClient$), which adds [tracing][otel4s-tracing] and [metrics][otel4s-metrics] on every call to the underlying queue system.

You can opt-in for either one of them or both.

```scala mdoc:compile-only
import cats.effect.IO

import com.commercetools.queue.QueueClient
import com.commercetools.queue.otel4s._

import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.trace.Tracer

val rawClient: QueueClient[IO] = ???

implicit val meter: Meter[IO] = ???
implicit val tracer: Tracer[IO] = ???

val withMetrics = rawClient.withMetrics()

val withTracing = rawClient.withTraces

val withBoth = rawClient.withMetricsAndTraces()
```

The extension methods adding metrics take an optional parameter indicating the name of the counter that counts the calls to the queue system. The default is defined in `MeasuringQueueClient.defaultRequestMetricsName`.

```scala mdoc
import com.commercetools.queue.otel4s.MeasuringQueueClient

MeasuringQueueClient.defaultRequestMetricsName
```

[otel4s]: https://typelevel.org/otel4s/
[otel4s-tracing]: https://typelevel.org/otel4s/instrumentation/tracing.html
[otel4s-metrics]: https://typelevel.org/otel4s/instrumentation/metrics.html
