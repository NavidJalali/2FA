package model

import syntax.ByteArraySyntax.ByteArrayOps

import java.util.Base64
import scala.util.Try

case class HexString private(underlyingBytes: Array[Byte], underlyingString: String) {
  def toBytes: Array[Byte] = underlyingBytes

  def +(other: HexString): HexString =
    HexString(underlyingBytes ++ other.underlyingBytes)

  override def toString: String = underlyingString

  def toBase64: String = Base64.getEncoder.encodeToString(underlyingBytes)
}

object HexString {
  private def toDigit(hexChar: Char): Option[Int] = {
    val digit = Character.digit(hexChar, 16)
    if (digit == -1) None else Some(digit)
  }

  private def hexToByte(hexString: String): Option[Byte] =
    for {
      firstDigit <- toDigit(hexString.charAt(0))
      secondDigit <- toDigit(hexString.charAt(1))
    } yield ((firstDigit << 4) + secondDigit).toByte

  private def toByteArray(hexString: String): Option[Array[Byte]] =
    if (hexString.length % 2 == 1) None
    else if (hexString.isEmpty) Some(Array.empty)
    else
      hexString
        .zip(hexString.tail)
        .map { case (x, y) => hexToByte(hexString.substring(x, y)) }
        .foldLeft(Option.empty[Array[Byte]]) {
          case (Some(acc), Some(byte)) => Some(acc :+ byte)
          case _ => None
        }

  def apply(bytes: Array[Byte]): HexString =
    HexString(bytes, bytes.toHexString)

  def fromString(string: String): Option[HexString] =
    toByteArray(string).map(bytes => HexString(bytes, string))

  def fromBase64(string: String): Option[HexString] =
    Try(Base64.getDecoder.decode(string)).toOption.map(HexString(_))
}
