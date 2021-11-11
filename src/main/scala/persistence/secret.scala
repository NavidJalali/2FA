package persistence

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import model.{SecretId, UserId}
import slick.interop.zio.DatabaseProvider
import slick.lifted.{ForeignKeyQuery, Index, ProvenShape, Tag}
import slick.jdbc.H2Profile.api._
import slick.interop.zio.syntax._
import zio.{Has, IO, ZIO, ZLayer}

import java.util.UUID

case class Secret(secretId: SecretId, ownerId: UserId, value: String)

object Secret {

  implicit val secretCodec: Codec[Secret] = deriveCodec

  type SecretTuple = (UUID, UUID, String)

  def fromTuple(t: SecretTuple): Secret =
    t match {
      case (secretId, ownerId, value) => Secret(SecretId(secretId), UserId(ownerId), value)
    }

  def toTuple(m: Secret): Option[SecretTuple] =
    Some((
      m.secretId.id,
      m.ownerId.id,
      m.value
    ))
}

case class SharedSecretDatabaseModel(sharedId: Long, secretId: SecretId, userId: UserId)

object SharedSecretDatabaseModel {
  type SharedSecretTuple = (Long, UUID, UUID)

  def fromTuple(t: SharedSecretTuple): SharedSecretDatabaseModel =
    t match {
      case (sharedId, secretId, userId) => SharedSecretDatabaseModel(sharedId, SecretId(secretId), UserId(userId))
    }

  def toTuple(m: SharedSecretDatabaseModel): Option[SharedSecretTuple] =
    Some((
      m.sharedId,
      m.secretId.id,
      m.userId.id,
    ))
}

trait SecretRepository {
  def add(secret: Secret): IO[Throwable, SecretId]

  def getById(secretId: SecretId): IO[Throwable, Option[Secret]]

  def getSharedWith(userId: UserId): IO[Throwable, Seq[Secret]]

  def shareWith(secretId: SecretId, userId: UserId): IO[Throwable, Long]

  def upsert(updated: Secret): IO[Throwable, SecretId]
}

object SecretTable {
  class Secrets(tag: Tag) extends Table[Secret](
    _tableTag = tag,
    _tableName = "secrets"
  ) {
    def secretId: Rep[UUID] = column[UUID]("secret_id", O.PrimaryKey)

    def ownerId: Rep[UUID] = column[UUID]("owner_id")

    def value: Rep[String] = column[String]("value")

    def ownerFK: ForeignKeyQuery[UsersTable.Users, UserDatabaseModel] =
      foreignKey("fk_owner_id", ownerId, UsersTable.table)(
        _.userId, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade
      )

    def idxOwnerId: Index = index("idx_owner_id", ownerId, unique = false)

    def * : ProvenShape[Secret] = (secretId, ownerId, value) <>
      (Secret.fromTuple, Secret.toTuple)
  }

  val table = TableQuery[SecretTable.Secrets]
}

object SharedSecretTable {
  class SharedSecrets(tag: Tag) extends Table[SharedSecretDatabaseModel](
    _tableTag = tag,
    _tableName = "shared_secrets"
  ) {
    def sharedId: Rep[Long] = column[Long]("shared_id", O.PrimaryKey, O.AutoInc)

    def secretId: Rep[UUID] = column[UUID]("secret_id")

    def userId: Rep[UUID] = column[UUID]("user_id")

    def secretFK: ForeignKeyQuery[SecretTable.Secrets, Secret] =
      foreignKey("fk_secret_id", secretId, SecretTable.table)(
        _.secretId, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade
      )

    def userFK: ForeignKeyQuery[UsersTable.Users, UserDatabaseModel] =
      foreignKey("fk_user_id", userId, UsersTable.table)(
        _.userId, onUpdate = ForeignKeyAction.Restrict, onDelete = ForeignKeyAction.Cascade
      )

    def idxSecretId: Index = index("idx_secret_id", secretId, unique = false)

    def idxUserId: Index = index("idx_user_id", userId, unique = false)

    def * : ProvenShape[SharedSecretDatabaseModel] = (sharedId, secretId, userId) <>
      (SharedSecretDatabaseModel.fromTuple, SharedSecretDatabaseModel.toTuple)
  }

  val table = TableQuery[SharedSecretTable.SharedSecrets]
}

object SlickSecretsRepository {
  val live: ZLayer[Has[DatabaseProvider], Throwable, Has[SecretRepository]] =
    ZLayer.fromServiceM { db =>
      db.profile.flatMap { profile =>
        import profile.api._

        val initialize: ZIO[Has[DatabaseProvider], Throwable, Unit] =
          ZIO.fromDBIO(SecretTable.table.schema.createIfNotExists) *>
            ZIO.fromDBIO(SharedSecretTable.table.schema.createIfNotExists)

        val repository = new SecretRepository {
          private val secrets = SecretTable.table
          private val sharedSecrets = SharedSecretTable.table

          override def add(secret: Secret): IO[Throwable, SecretId] =
            ZIO.fromDBIO(secrets += secret)
              .as(secret.secretId)
              .provide(Has(db))

          override def getById(secretId: SecretId): IO[Throwable, Option[Secret]] =
            ZIO.fromDBIO(secrets.filter(_.secretId === secretId.id).result)
              .map(_.headOption)
              .provide(Has(db))

          override def getSharedWith(userId: UserId): IO[Throwable, Seq[Secret]] =
            ZIO.fromDBIO(
              sharedSecrets
                .join(secrets)
                .on { case (shared, secret) => shared.secretId === secret.secretId }
                .filter { case (shared, _) => shared.userId === userId.id }
                .map { case (_, secret) => secret }
                .result
            )
              .provide(Has(db))

          override def shareWith(secretId: SecretId, userId: UserId): IO[Throwable, Long] =
            ZIO.fromDBIO(
              (sharedSecrets returning sharedSecrets.map(_.sharedId)) += SharedSecretDatabaseModel(0L, secretId, userId)
            )
              .provide(Has(db))

          override def upsert(updated: Secret): IO[Throwable, SecretId] =
            ZIO.fromDBIO {
              implicit ec => (for {
                secretOpt <- secrets.filter(_.secretId === updated.secretId.id).result.headOption
                id <- secretOpt.fold[DBIOAction[UUID, NoStream, Effect.Write]](
                  (secrets returning secrets.map(_.secretId)) += updated
                )(secret => secrets.map(_.value).update(updated.value).map(_ => secret.secretId.id))
              } yield id).transactionally
            }
              .map(SecretId(_))
              .provide(Has(db))
        }

        initialize.as(repository).provide(Has(db))
      }
    }
}
