package com.commercetools.queue

/**
 * Abstraction over how to deserialize data from string.
 */
trait Deserializer[T] {
  def deserialize(s: String): Either[Throwable, T]
}

object Deserializer {

  implicit val stringDeserializer: Deserializer[String] = Right(_)

}
