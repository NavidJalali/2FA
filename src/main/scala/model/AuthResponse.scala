package model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class AuthResponse(authHeader: Option[String])

object AuthResponse {
  val empty: AuthResponse = AuthResponse(None)

  def fromJwt(jwt: String): AuthResponse = AuthResponse(Some(s"bearer $jwt"))

  implicit val authResponseCodec: Codec[AuthResponse] = deriveCodec
}
