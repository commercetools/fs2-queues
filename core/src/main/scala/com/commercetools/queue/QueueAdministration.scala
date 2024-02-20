package com.commercetools.queue

import cats.effect.IO
import scala.concurrent.duration.FiniteDuration

trait QueueAdministration {

  def create(name: String, messageTTL: FiniteDuration, lockTTL: FiniteDuration): IO[Unit]

  def delete(name: String): IO[Unit]

  def exists(name: String): IO[Boolean]
}
