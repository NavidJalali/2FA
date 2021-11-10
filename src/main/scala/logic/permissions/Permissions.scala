package logic.permissions

import configuration.Configuration
import cryptography.Crypto
import logic.permissions.Permissions.Error.{DatabaseError, InvalidPrivateKey, NoSuchPermission, NoSuchUser, Unauthorized}
import model.{HexString, PermissionId, ServiceError, UserId}
import persistence.{Permission, PermissionRepository, UserDatabaseModel, UserRepository}
import syntax.UUIDSyntax.UUIDOps
import zhttp.http.Status
import zio.{Has, IO, ZLayer}
import syntax.ByteArraySyntax._

import java.util.UUID

object Permissions {
  trait Service {
    def add(requester: UserId): IO[Error, PermissionId]

    def add(requester: UserDatabaseModel): IO[Error, PermissionId]

    def getAllPending(userId: UserId): IO[Error, Seq[Permission]]

    def edit(permission: Permission): IO[Error, PermissionId]

    def grant(grantorKey: String, permissionId: PermissionId): IO[Error, PermissionId]
  }

  val live: ZLayer[Configuration with Crypto with Has[UserRepository] with Has[PermissionRepository], Nothing, Has[Service]] =
    ZLayer.fromServices[Configuration.FinalConfig, Crypto.Service, UserRepository, PermissionRepository, Service] {
      (configuration, crypto, users, permissions) =>
        new Service {
          private def generateToken(initiatorKey: String, grantorKey: String, permissionId: PermissionId): IO[Error, String] =
            for {
              payload <-
                IO.fromOption(
                  for {
                    iKey <- HexString.fromBase64(initiatorKey)
                    gKey <- HexString.fromBase64(grantorKey)
                    pid = HexString(permissionId.id.toBytes)
                  } yield (iKey + gKey + pid).toBase64
                ).orElseFail(InvalidPrivateKey)

              hmac =
                crypto.generateHMAC(
                  configuration.permissionSecret.toBase64,
                  payload
                ).toBase64
            } yield hmac

          override def add(requester: UserId): IO[Error, PermissionId] = {
            val permissionId = PermissionId(UUID.randomUUID())
            for {
              requesterDbRes <- users.getById(requester)
                .mapError(DatabaseError)
                .flatMap({
                  case Some(dbModel) => IO.succeed(dbModel)
                  case None => IO.fail(NoSuchUser(requester))
                })

              (requesterUser, requesterDbModel) = requesterDbRes

              mateDbRes <- users.getById(requesterUser.mateId)
                .mapError(DatabaseError)
                .flatMap({
                  case Some(dbModel) => IO.succeed(dbModel)
                  case None => IO.fail(NoSuchUser(requesterUser.mateId))
                })

              (mateUser, mateDbModel) = mateDbRes

              hmac <- generateToken(requesterDbModel.privateKey, mateDbModel.privateKey, permissionId)

              result <- permissions.add(
                Permission(
                  permissionId = permissionId, requester = requester, grantor = mateUser.userId, token = hmac, granted = false
                )
              ).mapError(DatabaseError)
            } yield result
          }

          override def add(requester: UserDatabaseModel): IO[Error, PermissionId] = {
            val permissionId = PermissionId(UUID.randomUUID())
            for {
              mateDbRes <- users.getById(requester.mateId)
                .mapError(DatabaseError)
                .flatMap({
                  case Some(dbModel) => IO.succeed(dbModel)
                  case None => IO.fail(NoSuchUser(requester.mateId))
                })

              (mateUser, mateDbModel) = mateDbRes

              hmac <- generateToken(requester.privateKey, mateDbModel.privateKey, permissionId)

              result <- permissions.add(
                Permission(
                  permissionId = permissionId, requester = requester.userId, grantor = mateUser.userId, token = hmac, granted = false
                )
              ).mapError(DatabaseError)
            } yield result
          }

          override def getAllPending(userId: UserId): IO[Error, Seq[Permission]] =
            permissions.getByGrantor(userId).mapError(DatabaseError)

          override def edit(permission: Permission): IO[Error, PermissionId] =
            permissions.upsert(permission).mapError(DatabaseError)

          override def grant(grantorKey: String, permissionId: PermissionId): IO[Error, PermissionId] =
            for {
              permission <- permissions
                .getById(permissionId)
                .mapError(DatabaseError)
                .flatMap {
                  case Some(permission) => IO.succeed(permission)
                  case None => IO.fail(NoSuchPermission(permissionId))
                }

              _ <- IO.fail(NoSuchPermission(permissionId))
                .unless(permission.permissionId == permissionId)

              initiatorKey <- users
                .getById(permission.requester)
                .mapError(DatabaseError)
                .flatMap {
                  case Some((_, initiatorDbModel)) => IO.succeed(initiatorDbModel.privateKey)
                  case None => IO.fail(Unauthorized)
                }

              token <- generateToken(initiatorKey, grantorKey, permissionId)

              result <-
                if (token == permission.token)
                  edit(permission.copy(granted = true))
                else IO.fail(Unauthorized)

            } yield result
        }
    }

  sealed trait Error extends ServiceError

  object Error {
    case class DatabaseError(cause: Throwable) extends Error {
      override val status: Status = Status.INTERNAL_SERVER_ERROR
    }

    case class NoSuchPermission(permissionId: PermissionId) extends Error {
      override val status: Status = Status.NOT_FOUND
    }

    case class NoSuchUser(userId: UserId) extends Error {
      override val status: Status = Status.NOT_FOUND
    }

    case object Unauthorized extends Error {
      override val status: Status = Status.UNAUTHORIZED
    }

    case object InvalidPrivateKey extends Error {
      override val status: Status = Status.INTERNAL_SERVER_ERROR
    }
  }
}