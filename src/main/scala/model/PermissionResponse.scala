package model

import io.circe.generic.semiauto.deriveCodec
import io.circe.{Codec, Decoder, Encoder}

import java.sql.Timestamp

case class PermissionResponse(permissionId: PermissionId, requester: UserId, createdAt: Timestamp)

object PermissionResponse {
  implicit val timestampCodec: Codec[Timestamp] = Codec.from(
    c => Decoder.decodeLong.map(s => new Timestamp(s)).apply(c),
    ts => Encoder.encodeLong.apply(ts.getTime)
  )

  implicit val permissionResponseCodec: Codec[PermissionResponse] = deriveCodec
}
