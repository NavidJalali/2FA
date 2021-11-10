package cryptography

import org.mindrot.jbcrypt.BCrypt
import zio.{Has, IO, UIO, ULayer, ZLayer}

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.util.Try

object Crypto {
  trait Service {
    def generateSecret(length: Int): Array[Byte]

    def generateHMAC(secret: String, payload: String): Array[Byte]

    def hashPassword(password: String): IO[Throwable, String]

    def generateSalt: UIO[String]

    def validatePassword(password: String, hash: String): IO[Throwable, Boolean]
  }

  val live: ULayer[Has[Service]] =
    ZLayer.succeed(
      new Service {
        private val secureRandom = new SecureRandom()

        override def generateHMAC(secret: String, payload: String): Array[Byte] = {
          val secretSpec = new SecretKeySpec(secret.getBytes, "SHA256")
          val mac = Mac.getInstance("HmacSHA256")
          mac.init(secretSpec)
          mac.doFinal(payload.getBytes)
        }

        override def generateSecret(length: Int): Array[Byte] = {
          val bytes = new Array[Byte](length)
          secureRandom.nextBytes(bytes)
          bytes
        }

        override def hashPassword(password: String): IO[Throwable, String] =
          generateSalt.flatMap(salt => IO.fromTry(Try(BCrypt.hashpw(password, salt))))

        override def generateSalt: UIO[String] = UIO.succeed(BCrypt.gensalt(10))

        override def validatePassword(password: String, hash: String): IO[Throwable, Boolean] =
          IO.fromTry(Try(BCrypt.checkpw(password, hash)))
      }
    )
}
