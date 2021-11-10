package persistence

import model.{PermissionId, UserId}
import slick.lifted.{ForeignKeyQuery, Index, ProvenShape}
import slick.interop.zio.DatabaseProvider
import slick.interop.zio.syntax._
import slick.jdbc.H2Profile.api._
import zio.{Has, IO, ZIO, ZLayer}

import java.util.UUID

case class Permission(permissionId: PermissionId, requester: UserId, grantor: UserId, token: String, granted: Boolean)

object Permission {
  type PermissionTuple = (UUID, UUID, UUID, String, Boolean)

  def fromTuple(t: PermissionTuple): Permission =
    t match {
      case (permissionId, requester, grantor, token, granted) =>
        Permission(PermissionId(permissionId), UserId(requester), UserId(grantor), token, granted)
    }

  def toTuple(m: Permission): Option[PermissionTuple] =
    Some((
      m.permissionId.id,
      m.requester.id,
      m.grantor.id,
      m.token,
      m.granted
    ))
}

trait PermissionRepository {
  def add(permission: Permission): IO[Throwable, PermissionId]

  def getById(permissionId: PermissionId): IO[Throwable, Option[Permission]]

  def getByGrantor(grantor: UserId): IO[Throwable, Seq[Permission]]

  def upsert(updated: Permission): IO[Throwable, PermissionId]
}

object PermissionsTable {
  class Permissions(tag: Tag) extends Table[Permission](
    _tableTag = tag,
    _tableName = "permissions"
  ) {
    def permissionId: Rep[UUID] = column[UUID]("permission_id", O.PrimaryKey)

    def requester: Rep[UUID] = column[UUID]("requester")

    def grantor: Rep[UUID] = column[UUID]("grantor")

    def token: Rep[String] = column[String]("token")

    def granted: Rep[Boolean] = column[Boolean]("granted")

    def requesterFK: ForeignKeyQuery[UsersTable.Users, UserDatabaseModel] =
      foreignKey("fk_requester_id", requester, UsersTable.table)(
        _.userId, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade
      )

    def grantorFK: ForeignKeyQuery[UsersTable.Users, UserDatabaseModel] =
      foreignKey("fk_grantor_id", grantor, UsersTable.table)(
        _.userId, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade
      )

    def idxGrantor: Index = index("idx_grantor", grantor, unique = false)

    def * : ProvenShape[Permission] = (permissionId, requester, grantor, token, granted) <>
      (Permission.fromTuple, Permission.toTuple)
  }

  val table = TableQuery[PermissionsTable.Permissions]
}

object SlickPermissionRepository {
  val live: ZLayer[Has[DatabaseProvider], Throwable, Has[PermissionRepository]] =
    ZLayer.fromServiceM { db =>
      db.profile.flatMap { profile =>
        import profile.api._

        val initialize = ZIO.fromDBIO(PermissionsTable.table.schema.createIfNotExists)

        val repository = new PermissionRepository {
          private val permissions = PermissionsTable.table

          override def add(permission: Permission): IO[Throwable, PermissionId] =
            ZIO.fromDBIO(permissions += permission)
              .as(permission.permissionId)
              .provide(Has(db))

          override def getById(permissionId: PermissionId): IO[Throwable, Option[Permission]] =
            ZIO.fromDBIO(permissions.filter(_.permissionId === permissionId.id).result)
              .map(_.headOption)
              .provide(Has(db))

          override def getByGrantor(grantor: UserId): IO[Throwable, Seq[Permission]] =
            ZIO.fromDBIO(
              permissions
                .filter(_.grantor === grantor.id)
                .result
            )
              .provide(Has(db))

          override def upsert(updated: Permission): IO[Throwable, PermissionId] =
            ZIO.fromDBIO {
              implicit ec =>
                (for {
                  permissionOpt <- permissions.filter(_.permissionId === updated.permissionId.id).result.headOption
                  id <- permissionOpt.fold[DBIOAction[UUID, NoStream, Effect.Write]](
                    (permissions returning permissions.map(_.permissionId)) += updated
                  )(permission => permissions.update(updated).map(_ => permission.permissionId.id))
                } yield id).transactionally
            }
              .map(PermissionId(_))
              .provide(Has(db))
        }

        initialize.as(repository).provide(Has(db))
      }
    }
}
