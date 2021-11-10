package syntax

import java.util.Base64

object ByteArraySyntax {
  private def byteToHex(byte: Byte): String = {
    val hexDigits = new Array[Char](2)
    hexDigits(0) = Character.forDigit((byte >> 4) & 0xF, 16)
    hexDigits(1) = Character.forDigit(byte & 0xF, 16)
    new String(hexDigits)
  }

  implicit class ByteArrayOps(private val bytes: Array[Byte]) {
    def toHexString: String = {
      val hexStringBuffer = new StringBuffer
      for (i <- bytes.indices) {
        hexStringBuffer.append(byteToHex(bytes(i)))
      }
      hexStringBuffer.toString
    }

    def toBase64: String = Base64.getEncoder.encodeToString(bytes)
  }
}
