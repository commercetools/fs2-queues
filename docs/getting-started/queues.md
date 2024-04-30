# Common Queue Interface

The library offers both low and high level possibilities, making it possible to have fine grained control over queue pulling, or just focusing on processing, delegating message management to the library.

The design of the API is the result of the common usage patterns and how the various client SDKs are designed.
There are several views possible on a queue:

 - as a `QueuePublisher` when you only need to [publish messages](publishing.md) to an existing queue.
 - as a `QueueSubscriber` when you only need to [subscribe](subscribing.md) to an existing queue.
 - as a `QueueAdministration` when you need to [manage](administration.md) queues (creation, deletion, ...).

The entry point is the `QueueClient` factory for each underlying queue system.
For each supported queue provider, you can get an instance of the `QueueClient`, please refer to the [Providers](../systems/index.md) section to see how.
