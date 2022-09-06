package encrypteddb.client

import cats.effect.{MonadCancel, Concurrent}
import cats.effect.std.Console
import fs2.io.net.{Network, Socket}
import fs2.{Chunk, Stream, text}
import com.comcast.ip4s.*
import fs2.io.file.{Files, Path}
import cats.syntax.all.*

import java.io.FileNotFoundException

object Client:

    val destinationFile = "destination.jpg"
    val sourceFile = "meditate_monke.jpg"

    def clientFolderName =  "clientFiles/"

    class InvalidServerResponse(str: String) extends Exception(str)

    def connect[F[_]: Concurrent: Network:Console](address: SocketAddress[Host]): Stream[F, Socket[F]] =
      Stream.exec(Console[F].println(s"Trying to connect to $address")) ++
      Stream.resource(Network[F].client(address))

    def push[F[_]: Concurrent: Network: Console: Files](address: SocketAddress[Host], file: String): Stream[F, Unit] =
      Stream.exec(Console[F].println(s"Trying to push file $file to $address")) ++
        connect(address)
          .flatMap { socket =>
            sendMessage(socket, "PUSH"+ " " + file) ++
                getValidatedResponse(socket, file) ++
                  pushFile(address, socket, file) ++
                    Stream.exec(Console[F].println(s"Pushing data done"))
          }


    def get[F[_]: Concurrent: Network: Console: Files](address: SocketAddress[Host], file: String): Stream[F, Unit] =
      Stream.exec(Console[F].println(s"Trying to get a file from $address")) ++
        connect(address)
          .flatMap { socket =>
            sendMessage(socket, "GET" + " " + file) ++
              Stream.exec(Console[F].println(s"Receiving data from $address")) ++
                getValidatedResponse(socket, file) ++
                  getFile(address, socket, file) ++
                    Stream.exec(Console[F].println(s"Receive data done"))
          }

    def sendMessage[F[_]: Network](socket: Socket[F], command: String): Stream[F, Nothing] =
      Stream(command)
        .interleave(Stream.constant("\n"))
        .through(text.utf8.encode)
        .through(socket.writes)


    def pushFile[F[_]: Concurrent: Network: Console: Files](address: SocketAddress[Host], socket: Socket[F], file: String): Stream[F, Nothing] =
      Stream.eval(Files[F].exists(Path(clientFolderName + file)))
        .flatMap { fileExist =>
          if (fileExist)
            Stream.exec(Console[F].println(s"Pushing data to $address")) ++
              Files[F].readAll(Path(clientFolderName + file))
                .through(socket.writes)
          else
            Stream.raiseError(new FileNotFoundException(s"File: $file does not exist"))
        }

    def getFile[F[_]: Concurrent: Network: Console: Files](address: SocketAddress[Host], socket: Socket[F], file: String): Stream[F, Nothing] =
        Stream.exec(Console[F].println(s"Receiving data from $address")) ++
          socket.reads
            .through(Files[F].writeAll(Path(clientFolderName + file)))

    def getMessage[F[_]: Concurrent: Network: Console: Files]( socket: Socket[F], file: String): Stream[F, String] =
      socket.reads
        .through(text.utf8.decode)
        .through(text.lines)
        .head

    def validateResponse[F[_]: Concurrent: Network: Console: Files]( response: String): Stream[F, Nothing] =
      response.toUpperCase() match
        case "OK" => Stream.empty
        case _    => Stream.raiseError(new InvalidServerResponse(s"Server responded: $response"))

    def getValidatedResponse[F[_]: Concurrent: Network: Console: Files](socket: Socket[F], file: String): Stream[F, Nothing] =
      getMessage(socket, file)
        .flatMap(validateResponse(_))



