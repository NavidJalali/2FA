import zio.Has

package object cryptography {
  type Crypto = Has[Crypto.Service]
}
