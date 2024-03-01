package com.commercetools.queue

import io.circe.parser.parse
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

object circe {

  implicit def serializerForEncoder[T: Encoder]: Serializer[T] =
    _.asJson.noSpaces

  implicit def deserializerForDecoder[T: Decoder]: Deserializer[T] =
    parse(_).flatMap(_.as[T])

}
