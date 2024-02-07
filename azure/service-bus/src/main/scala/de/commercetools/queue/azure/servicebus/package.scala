package de.commercetools.queue.azure

import cats.effect.IO
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

package object servicebus {

  def fromBlockingMono[T](mono: Mono[T]): IO[T] =
    IO.async { cb =>
      IO.delay {
        mono
          .subscribeOn(Schedulers.boundedElastic())
          .subscribe(res => cb(Right(res)), t => cb(Left(t)))
      }.map(disposable => Some(IO.delay(disposable.dispose())))
    }

}
