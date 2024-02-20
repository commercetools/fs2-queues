package com.commercetools.queue

/**
 * Abstraction over data serialization to string.
 */
trait Serializer[T] {
  def serialize(t: T): String
}

object Serializer {

  implicit val stringSerializer: Serializer[String] = identity(_)

}
