package model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import persistence.Secret

case class User(userId: UserId,
                mateId: UserId,
                username: String,
                secrets: Vector[Secret]) {
  def makeAuthHeader: AuthHeader = AuthHeader(userId, username)
}

object User {
  implicit val userCodec: Codec[User] = deriveCodec
}
