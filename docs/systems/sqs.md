# AWS SQS

You can create a client to service bus queues by using the [AWS SQS][sqs] module.

```scala
libraryDependencies += "com.commercetools" %% "fs2-queues-aws-sqs" % "@SNAPSHOT_VERSION@"
```

For instance you can create a managed client via a region and credentials as follows.

```scala mdoc:compile-only
import cats.effect.IO
import com.commercetools.queue.aws.sqs._
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider

val region = Region.US_EAST_1 // your region
val credentials = DefaultCredentialsProvider.create() // however you want to authenticate

SQSClient[IO](region, credentials).use { client =>
  ???
}
```

The client is managed, meaning that it uses a dedicated HTTP connection pool that will get shut down upon resource release.

If integrating with an existing code base where you already have an instance of `SdkAsyncHttpClient` that you would like to share, you can pass the optional `httpClient` parameter. If passed explicitly, the client is not closed when the resource is released, and it is up to the caller to manage it.

[sqs]: https://aws.amazon.com/sqs/
