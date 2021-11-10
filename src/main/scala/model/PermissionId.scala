package model

import io.circe.{Codec, Decoder, Encoder}

import java.util.UUID
import scala.util.Try

case class PermissionId(id: UUID) extends AnyVal
object PermissionId {
  def fromString(str: String): Either[String, PermissionId] = Try(UUID.fromString(str)).map(PermissionId(_)).toEither
    .fold(_ => Left(s"$str is not a valid SecretId"), Right(_))

  implicit val userIdCodec: Codec[PermissionId] = Codec.from(
    Decoder.decodeString.emap(fromString),
    Encoder.encodeString.contramap(_.id.toString)
  )
}
