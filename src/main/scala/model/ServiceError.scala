package model

import zhttp.http.{Header, HttpData, Response, Status}
import zio.Chunk

trait ServiceError {
  val message: Option[String] = None
  val headers: List[Header] = List.empty
  val status: Status

  def toResponse: Response[Any, Nothing] = Response.HttpResponse(
    status, headers, message.fold(HttpData.empty)(msg => HttpData.CompleteData(Chunk.fromArray(msg.getBytes())))
  )
}
