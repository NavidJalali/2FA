package model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import zio.duration.Duration

import java.time.Clock

case class AuthHeader(userId: UserId, username: String) {
  def toJwt(secret: String, expiration: Duration): String =
    Jwt.encode(
      JwtClaim {
        AuthHeader.authHeaderCodec(this)
          .noSpaces
      }
        .issuedNow(AuthHeader.clock)
        .expiresIn(expiration.toSeconds)(AuthHeader.clock),
      secret,
      JwtAlgorithm.HS512)
}

object AuthHeader {
  private val clock: Clock = Clock.systemUTC

  implicit val authHeaderCodec: Codec[AuthHeader] = deriveCodec
}
