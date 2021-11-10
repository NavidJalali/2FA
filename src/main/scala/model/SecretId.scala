package model

import io.circe.{Codec, Decoder, Encoder}

import java.util.UUID
import scala.util.Try

case class SecretId(id: UUID) extends AnyVal

object SecretId {
  def fromString(str: String): Either[String, SecretId] = Try(UUID.fromString(str)).map(SecretId(_)).toEither
    .fold(_ => Left(s"$str is not a valid SecretId"), Right(_))

  implicit val userIdCodec: Codec[SecretId] = Codec.from(
    Decoder.decodeString.emap(fromString),
    Encoder.encodeString.contramap(_.id.toString)
  )
}
