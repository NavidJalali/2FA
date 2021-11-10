package syntax

import model.HexString
import syntax.ByteArraySyntax.ByteArrayOps

import java.util.UUID

object UUIDSyntax {
  implicit class UUIDOps(private val uuid: UUID) {
    def toBytes: Array[Byte] = {
      val bytes = new Array[Byte](16)
      val msb = uuid.getMostSignificantBits
      val lsb = uuid.getLeastSignificantBits
      for (i <- 7 to 0 by -1) {
        bytes(i) = ((msb >> ((7 - i) * 8)) & 0xFF).toByte
        bytes(i + 8) = ((lsb >> ((7 - i) * 8)) & 0xFF).toByte
      }
      bytes
    }

    def toBase64: String = toBytes.toBase64

    def toHexString: HexString =
      HexString(toBytes)
  }
}
