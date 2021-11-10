package api

import configuration.Configuration
import io.circe.{Decoder, Encoder}
import io.circe.jawn.decode
import logic.auth.Auth
import logic.permissions.Permissions
import logic.secrets.Secrets
import model.{Accepted, AuthHeader, Credentials, SecretId, SharedSecret, ValueType}
import pdi.jwt.{Jwt, JwtAlgorithm}
import persistence.Secret
import zhttp.http._
import zio.duration.durationInt
import zio.{Chunk, UIO, ZIO, ZLayer}

import java.time.Clock
import java.util.UUID

object API {
  trait FinalAPI {
    val httpApp: HttpApp[Any, Nothing]
  }

  val badRequest: Response.HttpResponse[Any, Nothing] = Response.HttpResponse(
    Status.BAD_REQUEST, List.empty, HttpData.empty
  )

  val unauthorized: Response.HttpResponse[Any, Nothing] = Response.HttpResponse(
    Status.UNAUTHORIZED, List.empty, HttpData.empty
  )

  def respondWith[A: Encoder](a: A, headers: List[Header] = List.empty): Response.HttpResponse[Any, Nothing] = Response.HttpResponse(
    Status.OK,
    Header.contentTypeJson :: headers,
    content = HttpData.CompleteData(
      Chunk.fromArray(implicitly[Encoder[A]].apply(a).noSpaces.getBytes())
    )
  )

  def validate[A: Decoder](request: Request)(f: A => UIO[Response[Any, Nothing]]): ZIO[Any, Nothing, Response[Any, Nothing]] =
    request.getBodyAsString match {
      case Some(jsonString) =>
        decode[A](jsonString).fold(
          _ => ZIO.succeed(badRequest),
          a => f(a)
        )
      case None => ZIO.succeed(badRequest)
    }

  def live =
    ZLayer.fromServices[Auth.Service, Permissions.Service, Secrets.Service, Configuration.FinalConfig, FinalAPI] {
      (auth, permissions, secrets, config) =>
        new FinalAPI {
          private val clock: Clock = Clock.systemUTC

          def authOptional(headers: List[Header])(f: Option[AuthHeader] => UIO[Response[Any, Nothing]]): ZIO[Any, Nothing, Response[Any, Nothing]] =
            f(headers
              .find(_.name.toString.toLowerCase() == "authorization")
              .flatMap(_.value.toString.split(" ").toList match {
                case List(k, v) if k.toLowerCase == "bearer" => Some(v)
                case _ => None
              })
              .flatMap(str => Jwt.decode(str, config.authSecret.toBase64, Seq(JwtAlgorithm.HS512)).toOption)
              .flatMap(
                claim => claim.expiration.map(_ <= clock.millis()).flatMap(Option.when(_)(claim.content))
              )
              .flatMap(decode[AuthHeader](_).toOption))

          def authRequired(headers: List[Header])(f: AuthHeader => UIO[Response[Any, Nothing]]): ZIO[Any, Nothing, Response[Any, Nothing]] =
            authOptional(headers) {
              case Some(header) => f(header)
              case None => ZIO.succeed(unauthorized)
            }

          override val httpApp: HttpApp[Any, Nothing] =
            HttpApp.collectM {
              case Method.GET -> Root / "healthcheck" =>
                ZIO.succeed(Response.text("OK"))

              case Request((Method.GET, URL(Root / "me", _, _)), headers, _) =>
                authRequired(headers) {
                  authHeader =>
                    auth.authorized(authHeader.userId)
                      .map { case (user, _) => respondWith(user) }
                      .catchAll(error => ZIO.succeed(error.toResponse))
                }

              case request@Request((Method.POST, URL(Root / "secret", _, _)), headers, _) =>
                authRequired(headers) {
                  authHeader =>
                    validate[ValueType](request) {
                      valueType =>
                        secrets.add(Secret(
                          secretId = SecretId(UUID.randomUUID()), ownerId = authHeader.userId, value = valueType.value
                        ))
                          .map(respondWith(_))
                          .catchAll(err => ZIO.succeed(err.toResponse))
                    }
                }

              case request@Request((Method.POST, URL(Root / "secret" / "share", _, _)), headers, _) =>
                authRequired(headers) {
                  authHeader =>
                    validate[SharedSecret](request) {
                      shared =>
                        if (shared.userId == authHeader.userId) ZIO.succeed(badRequest)
                        else secrets.share(shared.secretId, shared.userId)
                          .map(respondWith(_))
                          .catchAll(err => ZIO.succeed(err.toResponse))
                    }
                }

              case request@Request((Method.POST, URL(Root / "auth", _, _)), headers, _) =>
                validate[Credentials](request) {
                  credentials =>
                    (for {
                      userDbRes <- auth.login(credentials.username, credentials.password)
                      (user, dbModel) = userDbRes

                      result <-
                        headers.find(_.name.toString.toLowerCase == "x-caller-scope")
                          .flatMap(header => Option.when(header.value.toString.toLowerCase == config.godScopeSecret)(()))
                        match {
                          case Some(_) =>
                            ZIO.succeed(Response.text(
                              AuthHeader(user.userId, user.username).toJwt(config.authSecret.toBase64, 1.hour)
                            ))

                          case None => permissions.add(dbModel).map(Accepted.fromPermissionId).map(respondWith(_))
                        }
                    } yield result)
                      .catchAll(error => ZIO.succeed(error.toResponse))
                }
            }
        }
    }
}
