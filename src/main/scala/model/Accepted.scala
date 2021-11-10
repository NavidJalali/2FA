package model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class Accepted(permissionId: PermissionId, pollUrl: String)

object Accepted {
  def fromPermissionId(permissionId: PermissionId): Accepted =
    Accepted(permissionId, s"/auth/poll/${permissionId.id}")

  implicit val acceptedCodec: Codec[Accepted] = deriveCodec
}
