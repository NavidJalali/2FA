package logic

import zio.Has

package object permissions {
  type Permissions = Has[Permissions.Service]
}
