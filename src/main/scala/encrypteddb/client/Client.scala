package encrypteddb.client

import cats.effect.{MonadCancel, Temporal}
import cats.effect.std.Console
import fs2.io.net.{Network, Socket}
import fs2.{Chunk, Stream, text}
import com.comcast.ip4s.*
import fs2.io.file.{Files, Path}
import cats.syntax.all._

object Client:

    val destinationFile = "destination.jpg"
    val sourceFile = "meditate_monke.jpg"

    def clientFolderName =  "clientFiles/"

    def connect[F[_]: Temporal: Network:Console](address: SocketAddress[Host]): Stream[F, Socket[F]] =
      Stream.exec(Console[F].println(s"Trying to connect to $address")) ++
      Stream.resource(Network[F].client(address))

    def push[F[_]: Temporal: Network: Console: Files](address: SocketAddress[Host], file: String): Stream[F, Unit] =
      Stream.exec(Console[F].println(s"Trying to push file $file to $address")) ++
        connect(address)
          .flatMap { socket =>
              sendCommand(socket, "PUSH"+ " " + file) ++
                  socket.reads
                    .through(text.utf8.decode)
                    .through(text.lines)
                    .head
                    .foreach(r => Console[F].println(s"responded: $r")) ++
                      Stream.exec(Console[F].println(s"Pushing data to $address")) ++
                        Files[F].readAll(Path(clientFolderName + file))
                          .through(socket.writes) ++
                        Stream.exec(Console[F].println(s"Pushing data done"))
          }

    def get[F[_]: Temporal: Network: Console: Files](address: SocketAddress[Host], file: String): Stream[F, Unit] =
      Stream.exec(Console[F].println(s"Trying to get a file from $address")) ++
        connect(address)
          .flatMap { socket =>
            sendCommand(socket, "GET" + " " + file) ++
              Stream.exec(Console[F].println(s"Receiving data from $address")) ++
                socket.reads
                  .through(Files[F].writeAll(Path(clientFolderName + file))) ++
                  Stream.exec(Console[F].println(s"Receive data done"))
          }

    def sendCommand[F[_]: Network](socket: Socket[F], command: String): Stream[F, Nothing] =
      Stream(command)
        .interleave(Stream.constant("\n"))
        .through(text.utf8.encode)
        .through(socket.writes)



