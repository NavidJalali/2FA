import zio.Has

package object api {
  type API = Has[API.FinalAPI]
}
