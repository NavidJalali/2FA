package model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class NewUser(username: String, password: String, mateId: UserId)

object NewUser {
  implicit val newUserCodec: Codec[NewUser] = deriveCodec
}
