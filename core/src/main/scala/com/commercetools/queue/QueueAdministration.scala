package com.commercetools.queue

import scala.concurrent.duration.FiniteDuration

trait QueueAdministration[F[_]] {

  def create(name: String, messageTTL: FiniteDuration, lockTTL: FiniteDuration): F[Unit]

  def delete(name: String): F[Unit]

  def exists(name: String): F[Boolean]

}
