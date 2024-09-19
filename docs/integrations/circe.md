# Circe

The circe module provides integration with the [circe][circe] library.

```scala
libraryDependencies += "com.commercetools" %% "fs2-queues-circe" % "@VERSION@"
```

It provides:

 - a @:api(Serializer) for each type `T` that has an implicit `io.circe.Encoder[T]` in scope.
 - a @:api(Deserializer) for each type `T` that has an implicit `io.circe.Decoder[T]` in scope.

To get this feature in your code base, import the following:

```scala mdoc
import com.commercetools.queue.circe._
```

[circe]: https://circe.github.io/circe/
