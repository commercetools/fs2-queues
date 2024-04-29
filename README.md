# Common Cloud Client Tools
[![Continuous Integration](https://github.com/commercetools/fs2-queues/actions/workflows/ci.yml/badge.svg)](https://github.com/commercetools/fs2-queues/actions/workflows/ci.yml)

Aims at providing a unified way of working with cloud queues (SQS, PubSub, Service Bus, ...).

All the abstractions are defined in the [`core`](core/) module. Other modules implement the abstraction for various queue systems and integration with other libraries.

For more documentation, head over to the [documentation website](https://commercetools.github.io/fs2-queues).

## Development

Following commands are useful when developing:
 - `sbt compile` compiles all the modules.
 - `sbt test` runs all the tests.
 - `sbt prePR` prepares the current branch before pushing and opening a PR. It ensures various static checks will pass.
 - `sbt docs/tlSitePreview` starts a local server with the built documentation site.
