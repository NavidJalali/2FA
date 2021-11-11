package persistence

import model.{User, UserId}
import slick.lifted.{CanBeQueryCondition, ForeignKeyQuery, Index, ProvenShape, Query, Tag}
import slick.interop.zio.DatabaseProvider
import slick.interop.zio.syntax._
import slick.jdbc.H2Profile.api._
import zio.{Has, IO, ZIO, ZLayer}

import java.util.UUID

case class UserDatabaseModel(userId: UserId,
                             mateId: UserId,
                             username: String,
                             passwordHash: String,
                             privateKey: String)

object UserDatabaseModel {
  type UserTuple = (UUID, UUID, String, String, String)

  def fromTuple(t: UserTuple): UserDatabaseModel =
    t match {
      case (userId, mateId, username, passwordHash, privateKey) =>
        UserDatabaseModel(UserId(userId), UserId(mateId), username, passwordHash, privateKey)
    }

  def toTuple(m: UserDatabaseModel): Option[UserTuple] =
    Some((
      m.userId.id,
      m.mateId.id,
      m.username,
      m.passwordHash,
      m.privateKey
    ))
}

trait UserRepository {
  def add(user: UserDatabaseModel): IO[Throwable, UserId]

  def getById(userId: UserId): IO[Throwable, Option[(User, UserDatabaseModel)]]

  def getByUsername(username: String): IO[Throwable, Option[(User, UserDatabaseModel)]]

  def upsert(updated: UserDatabaseModel): IO[Throwable, UserId]
}

object UsersTable {
  class Users(tag: Tag) extends Table[UserDatabaseModel](
    _tableTag = tag,
    _tableName = "users"
  ) {
    def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey)

    def mateId: Rep[UUID] = column[UUID]("mate_id")

    def username: Rep[String] = column[String]("username")

    def passwordHash: Rep[String] = column[String]("passwordHash")

    def privateKey: Rep[String] = column[String]("privateKey")

    def mateFK: ForeignKeyQuery[UsersTable.Users, UserDatabaseModel] =
      foreignKey("fk_mate_id", mateId, UsersTable.table)(
        _.userId, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Restrict
      )

    def idxUsername: Index = index("idx_username", username, unique = true)

    def * : ProvenShape[UserDatabaseModel] = (userId, mateId, username, passwordHash, privateKey) <>
      (UserDatabaseModel.fromTuple, UserDatabaseModel.toTuple)
  }

  val table = TableQuery[UsersTable.Users]
}

object SlickUserRepository {
  val live: ZLayer[Has[DatabaseProvider], Throwable, Has[UserRepository]] =
    ZLayer.fromServiceM { db =>
      db.profile.flatMap { profile =>
        import profile.api._

        val initialize =
          ZIO.fromDBIO(UsersTable.table.schema.createIfNotExists) *>
            ZIO.fromDBIO(SecretTable.table.schema.createIfNotExists).ignore *>
            ZIO.fromDBIO(SharedSecretTable.table.schema.createIfNotExists).ignore
        // Ignore errors because https://github.com/slick/slick/issues/1999#issuecomment-658303140

        val repository = new UserRepository {
          private val users = UsersTable.table
          private val secrets = SecretTable.table
          private val sharedSecrets = SharedSecretTable.table

          override def add(user: UserDatabaseModel): IO[Throwable, UserId] = {
            ZIO.fromDBIO(users += user)
              .as(user.userId)
              .provide(Has(db))
          }

          override def getById(userId: UserId): IO[Throwable, Option[(User, UserDatabaseModel)]] =
            getWithFilter(_.userId === userId.id, _.userId == userId)

          override def getByUsername(username: String): IO[Throwable, Option[(User, UserDatabaseModel)]] =
            getWithFilter(_.username === username, _.username == username)

          def getWithFilter[A <: Rep[_]](sqlFilter: UsersTable.Users => A,
                                         filter: UserDatabaseModel => Boolean = _ => true)
                                        (implicit ev: CanBeQueryCondition[A])
          : IO[Throwable, Option[(User, UserDatabaseModel)]] = {
            ZIO.fromDBIO(
              users
                .filter(sqlFilter)
                .joinLeft(secrets)
                .on { case (user, secret) => user.userId === secret.ownerId }
                .joinLeft(
                  sharedSecrets
                    .join(secrets)
                    .on { (shared, secret) => shared.secretId === secret.secretId }
                )
                .on { case ((user, _), (shared, _)) => user.userId === shared.userId }
                .map { case ((user, ownSecret), sharedSecret) => (user, ownSecret, sharedSecret) }
                .result
            )
              .map {
                seq =>
                  seq
                    .groupBy { case (user, _, _) => user }
                    .map {
                      case (user, product) => user -> product.flatMap {
                        case (_, maybeSecret, maybeTuple) => maybeSecret.toSeq ++ maybeTuple.map(_._2).toSeq
                      }
                    }.collectFirst {
                    case (user, secrets) if filter(user) =>
                      (User(user.userId, user.mateId, user.username, secrets.toVector), user)
                  }
              }
              .provide(Has(db))
          }

          override def upsert(updated: UserDatabaseModel): IO[Throwable, UserId] =
            ZIO.fromDBIO { implicit ec =>
              (for {
                userOpt <- users.filter(_.userId === updated.userId.id).result.headOption
                id <- userOpt.fold[DBIOAction[UUID, NoStream, Effect.Write]](
                  (users returning users.map(_.userId)) += updated
                )(user =>
                  users
                    .map(u => (u.mateId, u.passwordHash, u.privateKey))
                    .update((updated.mateId.id, updated.passwordHash, updated.passwordHash))
                    .map(_ => user.userId.id)
                )
              } yield id).transactionally
            }
              .map(UserId(_))
              .provide(Has(db))
        }

        initialize.as(repository).provide(Has(db))
      }
    }
}
