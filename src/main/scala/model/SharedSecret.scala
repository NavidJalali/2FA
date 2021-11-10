package model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class SharedSecret(secretId: SecretId, userId: UserId)

object SharedSecret {
  implicit val sharedSecretCodec: Codec[SharedSecret] = deriveCodec
}
