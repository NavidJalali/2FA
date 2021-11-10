import api.API
import com.typesafe.config.Config
import configuration.Configuration
import model.UserId
import zhttp.service._
import zhttp.service.server.ServerChannelFactory
import zio._

import java.util.UUID
import cryptography.Crypto
import logic.auth.Auth
import logic.permissions.Permissions
import logic.secrets.Secrets
import persistence.{PermissionRepository, SecretRepository, SlickPermissionRepository, SlickSecretsRepository, SlickUserRepository, UserDatabaseModel, UserRepository}
import slick.interop.zio.DatabaseProvider
import slick.jdbc.JdbcProfile
import syntax.ByteArraySyntax.ByteArrayOps
import zio.console.Console

object Main extends App {
  val configuration: ULayer[Has[Configuration.FinalConfig]] = Configuration.local

  val databaseProvider: ULayer[Has[DatabaseProvider]] = configuration >>>
    ((ZLayer.fromService[Configuration.FinalConfig, Config](_.databaseConfig) ++
      ZLayer.succeed[JdbcProfile](slick.jdbc.H2Profile)) >>> DatabaseProvider.live).orDie

  val userRepo: ZLayer[Any, Nothing, Has[UserRepository]] = databaseProvider >>> SlickUserRepository.live.orDie
  val permissionRepo: ZLayer[Any, Nothing, Has[PermissionRepository]] = databaseProvider >>> SlickPermissionRepository.live.orDie
  val secretsRepo: ZLayer[Any, Nothing, Has[SecretRepository]] = databaseProvider >>> SlickSecretsRepository.live.orDie

  val crypto: ULayer[Has[Crypto.Service]] = Crypto.live

  val auth: ZLayer[Any, Nothing, Has[Auth.Service]] = (userRepo ++ crypto) >>> Auth.live
  val permissions: ZLayer[Any, Nothing, Has[Permissions.Service]] = (configuration ++ crypto ++ userRepo ++ permissionRepo) >>> Permissions.live
  val secrets: ZLayer[Any, Nothing, Has[Secrets.Service]] = secretsRepo >>> Secrets.live

  val api: ZLayer[Any, Nothing, Has[API.FinalAPI]] = (auth ++ permissions ++ secrets ++ configuration) >>> API.live

  val live: ZLayer[Any, Nothing, Has[API.FinalAPI] with Has[Configuration.FinalConfig] with Has[UserRepository] with Has[Crypto.Service] with ServerChannelFactory with EventLoopGroup] =
    api ++ configuration ++ userRepo ++ crypto ++ ServerChannelFactory.auto ++ EventLoopGroup.auto(4)

  val app: ZIO[
    Console
      with EventLoopGroup
      with ServerChannelFactory
      with Crypto with
      Has[UserRepository]
      with Configuration
      with API,
    Nothing,
    ExitCode] =
    for {
      api <- ZIO.service[API.FinalAPI]

      config <- ZIO.service[Configuration.FinalConfig]

      users <- ZIO.service[UserRepository]

      crypto <- ZIO.service[Crypto.Service]

      pwd <- crypto.hashPassword("foobar").orDie

      privateKey = crypto.generateSecret(512)

      _ <- users
        .add(UserDatabaseModel(
          UserId(UUID.fromString("00000000-0000-0000-0000-000000000000")),
          UserId(UUID.fromString("00000000-0000-0000-0000-000000000000")),
          "god",
          pwd,
          privateKey.toBase64
        ))
        .mapError(err => new RuntimeException(err.toString))
        .orDie

      server = Server.port(config.port) ++
        Server.paranoidLeakDetection ++
        Server.app(api.httpApp)

      result <- server
        .make
        .use(_ => console.putStrLn(s"Server started on port ${config.port}") *> ZIO.never)
        .orDie
        .exitCode
    } yield result

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] =
    app.provideCustomLayer(live)
}
