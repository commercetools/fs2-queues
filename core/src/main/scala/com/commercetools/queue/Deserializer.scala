package com.commercetools.queue

import cats.effect.IO

/**
 * Abstraction over how to deserialize data from string.
 */
trait Deserializer[T] {
  def deserialize(s: String): IO[T]
}

object Deserializer {

  implicit val stringDeserializer: Deserializer[String] = IO.pure(_)

}
