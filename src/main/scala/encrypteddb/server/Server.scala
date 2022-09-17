package encrypteddb.server

import fs2.io.file.{Files, Path}
import cats.effect.{Async, Concurrent, Temporal}
import fs2.{text, Stream}
import fs2.io.net.{Network, Socket}
import com.comcast.ip4s.*
import cats.effect.std.Console
import cats.MonadError
import encrypteddb.CommonMethods._
import cats.syntax.all.*

import java.io.{File, FileNotFoundException}
import scala.concurrent.duration.*
import cats.effect.std.UUIDGen

import java.util.UUID

object Server:

  def serverFolderName = "serverFiles/"

  case class InvalidClientResponse(response: String) extends Exception(response)
  case class UnknownCommand(command: String)         extends Exception(command)

  case class Connected[F[_]](
      id: UUID,
      socket: Socket[F]
  )
  object Connected:
    def apply[F[_]: Async: UUIDGen](socket: Socket[F]): F[Connected[F]] =
      for {
        id <- UUIDGen[F].randomUUID
      } yield Connected(id, socket)

  // Server logic

  def startController[F[_]: Files: Network: Async: Console](port: Port): Stream[F, Nothing] =
    streamPrint(s"Listening on port: $port") ++
      handleUncomingConnexions(port)

  def handleUncomingConnexions[F[_]: Files: Network: Async: Console: UUIDGen](port: Port): Stream[F, Nothing] =
    Network[F]
      .server(port = Some(port))
      .map { client =>
        Stream
          .bracket(startConnection(client))(endConnection)
          .flatMap(handleClient)
          .scope
          .handleErrorWith { error =>
            sendError(client, error.getMessage) ++
              Stream.eval(client.endOfInput)
          }
          .drain
      }
      .parJoinUnbounded

  def startConnection[F[_]: Async: UUIDGen: Console](socket: Socket[F]): F[Connected[F]] =
    Connected[F](socket).flatTap(cClient => Console[F].println(s"New Client connected: ${cClient.id}"))

  def endConnection[F[_]: Async: Console](client: Connected[F]): F[Unit] =
    Console[F].println(s"Client disconnected: ${client.id}")

  def handleClient[F[_]: Files: Network: Async: Console](client: Connected[F]): Stream[F, Unit] =
    streamPrint(s"[${client.id}] Handling one connexion ") ++
      getMessage(client.socket)
        .flatMap(command => handler(command, client)) ++
      streamPrint(s"[${client.id}] Handled")

  // Controller
  // Routes

  def handler[F[_]: Files: Network: Async: Console](command: String, client: Connected[F]): Stream[F, Unit] =
    streamPrint(s"[${client.id}] Received command: $command") ++ {
      command.split(" ").toList match
        case "PUSH" :: file :: _ => handlePush(client, file)
        case "GET" :: file :: _  => handleGet(client, file)
        case _                   => handleUnknownCommand(client, command)
    }

  // Handlers

  def handlePush[F[_]: Files: Network: Async: Console](
      client: Connected[F],
      destination: String
  ): Stream[F, Unit] =
    streamPrint(s"[${client.id}] Handling PUSH") ++
      sendOkConnected(client) ++
      streamPrint(s"[${client.id}] Ready to receive data ") ++
      client.socket.reads
        .through(fileFromStream(_, (serverFolderName + destination))) ++
      streamPrint(s"[${client.id}] Data received")

  def handleGet[F[_]: Files: Network: Async: Console](client: Connected[F], file: String): Stream[F, Unit] =
    streamPrint(s"[${client.id}] Handling GET") ++
      sendOkConnected(client) ++
      getValidatedResponse(client) ++
      sendFileConnected(client, file) ++
      streamPrint(s"[${client.id}] Sending file: $file")

  def handleUnknownCommand[F[_]: Console: Concurrent](client: Connected[F], command: String): Stream[F, Nothing] =
    Stream.raiseError(UnknownCommand(command))

  // Network Primitives

  def sendFileConnected[F[_]: Async: Network: Console: Files](client: Connected[F], file: String): Stream[F, Unit] =
    streamPrint(s"[${client.id}] Sending file: $file") ++
      sendFile(client.socket, serverFolderName + file)

  // Message Primitives

  def sendMessageConnected[F[_]: Network: Concurrent](client: Connected[F], message: String): Stream[F, Unit] =
    sendMessage(client.socket, message)

  def getMessageConnected[F[_]: Network: Async](client: Connected[F]): Stream[F, String] =
    getMessage(client.socket)

  def sendError[F[_]: Files: Network: Concurrent](socket: Socket[F], message: String): Stream[F, Unit] =
    sendMessage(socket, message)

  def sendOkConnected[F[_]: Network: Concurrent](client: Connected[F]): Stream[F, Unit] =
    sendMessageConnected(client, "OK")

  def validateResponse[F[_]: Async: Network: Console: Files](response: String): Stream[F, Unit] =
    response.toUpperCase() match
      case "OK"     => Stream.empty
      case response => Stream.raiseError(InvalidClientResponse(response))

  def getValidatedResponse[F[_]: Async: Network: Console: Files](client: Connected[F]): Stream[F, Unit] =
    getMessageConnected(client)
      .flatMap(validateResponse(_))
