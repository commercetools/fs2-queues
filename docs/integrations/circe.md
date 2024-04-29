# Circe

The `fs2-queues-circe` provides integration with the [circe][circe] library.

It provides:

 - a @:api(com.commercetools.queue.Serializer) for each type `T` that has an implicit `io.circe.Encoder[T]` in scope.
 - a @:api(com.commercetools.queue.Deserializer) for each type `T` that has an implicit `io.circe.Decoder[T]` in scope.

To get this feature in your code base, import the following:

```scala mdoc
import com.commercetools.queue.circe._
```

[circe]: https://circe.github.io/circe/
