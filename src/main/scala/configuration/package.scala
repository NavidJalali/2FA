import zio.Has

package object configuration {
  type Configuration = Has[Configuration.FinalConfig]
}
