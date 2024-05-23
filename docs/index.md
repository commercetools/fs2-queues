# Home

`fs2-queues` is a library that provides interfaces for working with queue systems. It is an opinionated library unifying the ways of working with queues independently of the underlying system.

It integrates with various queue providers, such as [AWS SQS](systems/sqs.md) or [Azure Service Bus](systems/service-bus.md).

The library is composed of several modules, and is cross-compiled for Scala 2.13 and 3.3.

@:callout(warning)
This library is still in its early development stage, and the API is not entirely stabilized.
There might be some breaking changes occuring until it reaches version `1.0.0`.
@:@
