package model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class ValueType(value: String)
object ValueType {
  implicit val valueTypeCodec: Codec[ValueType] = deriveCodec
}
