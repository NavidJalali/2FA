package model

import io.circe.{Codec, Decoder, Encoder}

import java.util.UUID
import scala.util.Try

case class UserId(id: UUID) extends AnyVal

object UserId {
  def fromString(str: String): Either[String, UserId] = Try(UUID.fromString(str)).map(UserId(_)).toEither
    .fold(_ => Left(s"$str is not a valid UserId"), Right(_))

  implicit val userIdCodec: Codec[UserId] = Codec.from(
    Decoder.decodeString.emap(fromString),
    Encoder.encodeString.contramap(_.id.toString)
  )
}
