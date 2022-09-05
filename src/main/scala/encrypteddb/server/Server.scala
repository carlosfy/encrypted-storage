package encrypteddb.server

import fs2.io.file.{Files, Path}
import cats.effect.Concurrent
import fs2.{Stream, text}
import fs2.io.net.{Network, Socket}
import com.comcast.ip4s.*
import cats.effect.std.Console
import cats.MonadError

object Server:

    val destinationFile = "destination.jpg"

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
        command match
          case "PUSH" => processPush(socket, destinationFile) //TODO: Change when the command include the destination
          case unknownCommand => handleUnknownCommand(unknownCommand)
      }

    def processPush[F[_]: Files: Network: Concurrent: Console](socket: Socket[F], destination: String): Stream[F, Nothing] =
      Stream.exec(Console[F].println("Received PUSH command")) ++
      Stream("Ok")
        .interleave(Stream.constant("\n"))
        .through(text.utf8.encode)
        .through(socket.writes) ++
          Stream.exec(Console[F].println("Ready to receive data")) ++
            socket.reads
              .through(Files[F].writeAll(Path(destination)))

    def handleUnknownCommand[F[_]: Console: Concurrent](command: String): Stream[F, Nothing] =
      Stream.eval(Console[F].println(s"Received unknown command: $command")) >>
        Stream.raiseError(new Error("Unknown command"))
