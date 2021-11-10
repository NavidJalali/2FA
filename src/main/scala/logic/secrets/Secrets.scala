package logic.secrets

import logic.secrets.Secrets.Error.DatabaseError
import model.{SecretId, ServiceError, UserId}
import persistence.{Secret, SecretRepository}
import zhttp.http.Status
import zio.{Has, IO, ZLayer}

object Secrets {
  trait Service {
    def add(secret: Secret): IO[Error, SecretId]
    def share(secretId: SecretId, shareWith: UserId): IO[Error, Long]
    def edit(secret: Secret): IO[Error, SecretId]
  }

  val live: ZLayer[Has[SecretRepository], Nothing, Has[Service]] =
    ZLayer.fromService[SecretRepository, Service] {
      secrets =>
        new Service {
          override def add(secret: Secret): IO[Error, SecretId] =
            secrets.add(secret).mapError(DatabaseError)

          override def share(secretId: SecretId, shareWith: UserId): IO[Error, Long] =
            secrets.shareWith(secretId, shareWith).mapError(DatabaseError)

          override def edit(secret: Secret): IO[Error, SecretId] =
            secrets.upsert(secret).mapError(DatabaseError)
        }
    }

  sealed trait Error extends ServiceError

  object Error {
    case class DatabaseError(cause: Throwable) extends Error {
      override val status: Status = Status.INTERNAL_SERVER_ERROR
    }
  }
}
