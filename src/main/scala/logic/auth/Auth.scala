package logic.auth

import cryptography.Crypto
import logic.auth.Auth.Error.{CryptographyError, DatabaseError, NoSuchUser, WrongCredentials}
import model.{ServiceError, User, UserId}
import persistence.{UserDatabaseModel, UserRepository}
import syntax.ByteArraySyntax.ByteArrayOps
import zhttp.http.Status
import zio.{Has, IO, ZLayer}

import java.util.UUID

object Auth {
  trait Service {
    def login(username: String, password: String): IO[Error, (User, UserDatabaseModel)]

    def authorized(userId: UserId): IO[Error, (User, UserDatabaseModel)]

    def create(username: String, password: String, mateId: UserId): IO[Error, (User, UserDatabaseModel)]

    def edit(user: UserDatabaseModel): IO[Error, UserId]
  }

  val live: ZLayer[Has[UserRepository] with Crypto, Nothing, Has[Service]] =
    ZLayer.fromServices[UserRepository, Crypto.Service, Auth.Service] {
      (users, crypto) =>
        new Service {
          override def login(username: String, password: String): IO[Error, (User, UserDatabaseModel)] =
            for {
              dbRes <- users.getByUsername(username)
                .mapError(DatabaseError)
                .flatMap({
                  case Some(value) => IO.succeed(value)
                  case None => IO.fail(NoSuchUser)
                })

              (_, dbModel) = dbRes

              result <- crypto.validatePassword(password, dbModel.passwordHash)
                .mapError(CryptographyError)
                .flatMap(matched =>
                  if (matched) IO.succeed(dbRes) else IO.fail(WrongCredentials)
                )
            } yield result

          override def authorized(userId: UserId): IO[Error, (User, UserDatabaseModel)] =
            users.getById(userId)
              .mapError(DatabaseError)
              .flatMap({
                case Some(value) => IO.succeed(value)
                case None => IO.fail(NoSuchUser)
              })

          override def create(username: String, password: String, mateId: UserId): IO[Error, (User, UserDatabaseModel)] =
            for {
              passwordHash <- crypto.hashPassword(password).mapError(CryptographyError)

              privateKey = crypto.generateSecret(512).toBase64

              userId <- users.add(
                UserDatabaseModel(
                  UserId(UUID.randomUUID()),
                  mateId,
                  username,
                  passwordHash,
                  privateKey
                )
              ).mapError(DatabaseError)

              result <- users.getById(userId)
                .mapError(DatabaseError)
                .flatMap {
                  case Some(value) => IO.succeed(value)
                  case None => IO.fail(DatabaseError(new RuntimeException(
                    "Failed to fetch created user."
                  )))
                }
            } yield result

          override def edit(user: UserDatabaseModel): IO[Error, UserId] =
            users.upsert(user).mapError(DatabaseError)
        }
    }

  sealed trait Error extends ServiceError

  object Error {
    case class DatabaseError(cause: Throwable) extends Error {
      override val status: Status = Status.INTERNAL_SERVER_ERROR
    }

    case class CryptographyError(cause: Throwable) extends Error {
      override val status: Status = Status.INTERNAL_SERVER_ERROR
    }

    case object NoSuchUser extends Error {
      override val status: Status = Status.NOT_FOUND
    }

    case object WrongCredentials extends Error {
      override val status: Status = Status.UNAUTHORIZED
    }
  }
}
