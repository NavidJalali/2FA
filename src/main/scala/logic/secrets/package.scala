package logic

import zio.Has

package object secrets {
  type Secrets = Has[Secrets.Service]
}
