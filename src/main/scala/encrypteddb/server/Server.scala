package encrypteddb.server

import fs2.io.file.{Files, Path}
import cats.effect.{Concurrent, Temporal}
import fs2.{Stream, text}
import fs2.io.net.{Network, Socket}
import com.comcast.ip4s.*
import cats.effect.std.Console
import cats.MonadError

import java.io.{File, FileNotFoundException}

object Server:

    val destinationFile = "destination.jpg"
    val originFile = "meditate_monke.jpg"
    val prefix = new File(".").getCanonicalPath

    def serverFolderName =  "serverFiles/"

    def startController[F[_]: Files: Network: Concurrent: Console](port: Port): Stream[F, Nothing] =
        Stream.exec(Console[F].println(s"Listening on port: $port")) ++
          handleUncomingConnexions(port)

    def handleUncomingConnexions[F[_]: Files: Network: Concurrent: Console](port: Port): Stream[F, Nothing] =
      Network[F].server(port = Some(port)).map { client =>
        Stream.exec(Console[F].println("New client connected")) ++
        handleOneConnexion(client) ++
        Stream.exec(Console[F].println("Client Disconnect"))

      }.parJoin(10)

    def handleOneConnexion[F[_]: Files: Network: Concurrent: Console](socket: Socket[F]): Stream[F, Nothing] =
      Stream.exec(Console[F].println("Handling one connexion")) ++
      socket.reads
        .through(text.utf8.decode)
        .through(text.lines)
        .head
        .flatMap(command => handler(command, socket)) ++
        Stream.exec(Console[F].println("Handled"))

    def handler[F[_]: Files: Network: Concurrent: Console](command: String, socket: Socket[F]): Stream[F, Nothing] =
      Stream.exec(Console[F].println(s"command received: $command")) ++ {
        command.split(" ").toList match
          case "PUSH" :: file :: _ => handlePush(socket, file) //TODO: Change when the command include the destination
          case "GET" :: file :: _ => handleGet(socket, file)
          case _ => handleUnknownCommand(command)
      }

    def handlePush[F[_]: Files: Network: Concurrent: Console](socket: Socket[F], destination: String): Stream[F, Nothing] =
      Stream.exec(Console[F].println("Handling PUSH")) ++
        sendOk(socket) ++
          Stream.exec(Console[F].println("Ready to receive data")) ++
            socket.reads
              .through(Files[F].writeAll(Path(serverFolderName + destination))) ++
            Stream.exec(Console[F].println("Data received"))

    def handleGet[F[_]: Files: Network: Concurrent: Console](socket: Socket[F], origin: String): Stream[F, Nothing] =
      Stream.exec(Console[F].println("Handling GET")) ++
        pushFile(socket, origin) ++
          Stream.exec(Console[F].println(s"Sending data done"))


    def handleUnknownCommand[F[_]: Console: Concurrent](command: String): Stream[F, Nothing] =
      Stream.eval(Console[F].println(s"Received unknown command: $command")) >>
        Stream.raiseError(new Error("Unknown command"))


    def pushFile[F[_]: Concurrent: Network: Console: Files](socket: Socket[F], file: String): Stream[F, Nothing] =
      Stream.eval(Files[F].exists(Path(serverFolderName + file)))
        .flatMap { fileExist =>
          if (fileExist)
            sendOk(socket) ++
              Stream.exec(Console[F].println(s"Pushing data")) ++
              Files[F].readAll(Path(serverFolderName + file))
                .through(socket.writes)
          else
            sendError(socket)
        }

    def sendMessage[F[_]: Files: Network: Concurrent: Console](socket: Socket[F], response: String): Stream[F, Nothing] =
      Stream(response)
        .interleave(Stream.constant("\n"))
        .through(text.utf8.encode)
        .through(socket.writes)

    def sendOk[F[_]: Files: Network: Concurrent: Console](socket: Socket[F]): Stream[F, Nothing] =
      sendMessage(socket, "OK")

    def sendError[F[_]: Files: Network: Concurrent: Console](socket: Socket[F]): Stream[F, Nothing] =
      sendMessage(socket, "Error: FileNotFound")