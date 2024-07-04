# Providers

`fs2-queues` comes with several queue system implementations. Each of them implements the @:api(com.commercetools.queue.QueueClient) abstraction with the various interfaces it gives access to.

Each implementations comes with its own way to get access to a client, depending on the underlying SDK. Please have a look at the provider documentation to see the different ways to instantiate the clients.

## Add your own provider

To add a new queue system, you need to implement the @:api(com.commercetools.queue.QueueClient) abstraction and all abstractions it gives access to.
To validate your implementation, we provide a testkit, which runs a series of tests that need to pass to ensure the abstraction behavior is working. All what is needed to implement the integration tests in the testkit is to implement the @:api(com.commercetools.queue.testkit.QueueClientSuite) class and provide a way to instantiate your client as a `Resource`.

```scala mdoc:compile-only
import cats.effect.{IO,  Resource}
import com.commercetools.queue.{
  Deserializer,
  QueueClient,
  QueueAdministration,
  QueuePublisher,
  QueueStatistics,
  QueueSubscriber,
  Serializer
}
import com.commercetools.queue.testkit.QueueClientSuite

class MyQueueClient[F[_]] extends QueueClient[F] {
  def administration: QueueAdministration[F] = ???
  def statistics(name: String): QueueStatistics[F] = ???
  def publish[T: Serializer](name: String): QueuePublisher[F,T] = ???
  def subscribe[T: Deserializer](name: String): QueueSubscriber[F,T] = ???

}

object MyQueueClient {
  def apply[F[_]](): Resource[F, MyQueueClient[F]] = ???
}

// Running this test suite will run all the testkit test
class MyQueueClientSuite extends QueueClientSuite {

  override def client: Resource[IO, QueueClient[IO]] =
    MyQueueClient[IO]()

}
```
