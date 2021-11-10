package model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class Credentials(username: String, password: String)

object Credentials {
  implicit val credentialsCodec: Codec[Credentials] = deriveCodec
}
