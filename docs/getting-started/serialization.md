{% nav = true %}
# Data Serialization

The library uses a string encoding for the message payload that is published or received from a queue.
The proper string encoding/decoding is performed by the underlying SDK, allowing you to only focus on the string serialization part.

## Data `Serializer`

A @:api(com.commercetools.queue.Serializer) is defined as a _SAM interface_ that is basically a `T => String`. Defining a new one can be done easily by providing an implicit conversion function from the type `T` to serialize to a `String`.

For instance, adding a serializer for `Int`s can be done as follows.

```scala mdoc
import com.commercetools.queue.Serializer

implicit val serializer: Serializer[Int] = _.toString
```

The library provides natively a _no-op_ serializer for `String`s.

## Data `Deserializer`

A @:api(com.commercetools.queue.Deserializer) is defined as a _SAM interface_ that is basically a `String => Either[Throwable, T]`. Defining a new one can be done easily by providing an implicit conversion function from a `String` to serialize to either a value of the type `T` or an exception.

For instance, adding a deserializer for `Int`s can be done as follows.

```scala mdoc
import cats.syntax.either._

import com.commercetools.queue.Deserializer

implicit val deserializer: Deserializer[Int] = s => Either.catchNonFatal(s.toInt)
```

The library provides natively a _no-op_ deserializer for `String`s.

## Library integration

We provide integration with some well-established serialization libraries (e.g. to perform JSON serialization). Have a look at the _Library Integration_ section for your favorite library.
