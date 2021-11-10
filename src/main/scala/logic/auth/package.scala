package logic

import zio.Has

package object auth {
  type Auth = Has[Auth.Service]
}
