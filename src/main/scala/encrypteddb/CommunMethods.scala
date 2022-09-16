package encrypteddb

import cats.effect.{Async, Concurrent}
import cats.effect.std.Console
import encrypteddb.client.Client.clientFolderName
import fs2.io.file.{Files, Path}
import fs2.io.net.{Network, Socket}
import fs2.{Stream, text}

import java.io.FileNotFoundException
import scala.concurrent.duration.*

object CommunMethods:

  def sendMessage[F[_]: Network](socket: Socket[F], command: String): Stream[F, Nothing] =
    Stream(command)
      .interleave(Stream.constant("\n"))
      .through(text.utf8.encode)
      .through(socket.writes)

  def getMessage[F[_]: Concurrent: Network: Console: Files](socket: Socket[F]): Stream[F, String] =
    socket.reads
      .through(text.utf8.decode)
      .through(text.lines)
      .head

  def streamFromFile[F[_]: Concurrent: Network: Console: Files](path: String): Stream[F, Byte] =
    Stream
      .eval(Files[F].exists(Path(path)))
      .flatMap { fileExist =>
        if (fileExist)
          Stream.exec(Console[F].println(s"Reading from file: $path")) ++
            Files[F].readAll(Path(path))
        else
          Stream.raiseError(new FileNotFoundException(s"File: $path does not exist"))
      }

  def fileFromStream[F[_]: Concurrent: Console: Files](s: Stream[F, Byte], path: String): Stream[F, Nothing] =
    Stream.exec(Console[F].println(s"Writing file on $path")) ++
      s.through(Files[F].writeAll(Path(path)))

  def showChunks[F[_]: Async: Console, O](in: Stream[F, O], chunkSize: Int): Stream[F, O] =
    in.groupWithin(chunkSize, 300.millis)
      .flatMap{chunk =>
        println(chunk.size)
        println(chunk)
        Stream.chunk(chunk)
      }