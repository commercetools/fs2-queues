# Contributing to `fs2-queues`

There a several ways you can contribute to `fs2-queues`:
 - You found a bug? You can [open an issue][open-issue].
 - If you have an idea, found something missing, or just a question, you can also [open an issue][open-issue].
 - Code contributions are also welcome, you can [open a pull request][open-pr].
   - For coding conventions, please see below.

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
## Table of Contents

- [Documentation](#documentation)
- [Code formatting](#code-formatting)
- [Licensing](#licensing)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Documentation

The documentation is written in markdown and lives in the `docs` directory.
All scala code snippets must be valid, they are compiled and executed using [mdoc][mdoc].
To verify that your snippets work, run:

```shell
$ sbt readme/mdoc
```

## Code formatting

`fs2-queues` uses [scalafmt][scalafmt] to format its code and defines some [scalafix][scalafix] rules. Before submitting code contribution, be sure to have proper formatting by running

```shell
$ sbt prePR
```

and check the result.

## Licensing

`fs2-queues` is licensed under the Apache Software License 2.0. Opening a pull request is to be considered affirmative consent to incorporate your changes into the project, granting an unrestricted license to the Commercetools GmbH to distribute and derive new work from your changes, as per the contribution terms of ASL 2.0. You also affirm that you own the rights to the code you are contributing. All contributors retain the copyright to their own work.

[open-issue]: https://github.com/commercetools/fs2-queues/issues/new/choose
[open-pr]: https://github.com/commercetools/fs2-queues/pull/new/main
[scalafmt]: https://scalameta.org/scalafmt/
[scalafix]: https://scalacenter.github.io/scalafix/
[mdoc]: https://scalameta.org/mdoc/
