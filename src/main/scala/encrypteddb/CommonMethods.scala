package encrypteddb

import cats.effect.{Async, Concurrent}
import fs2.io.file.{Files, Path}
import fs2.io.net.{Network, Socket}
import fs2.{text, Chunk, Stream}

import java.io.FileNotFoundException
import scala.concurrent.duration.*

object CommonMethods:

  def sendMessage[F[_]: Network](socket: Socket[F], command: String): Stream[F, Unit] =
    Stream.eval(socket.write(Chunk.array(command.getBytes())))

  def sendOk[F[_]: Files: Network: Concurrent: Console](socket: Socket[F]): Stream[F, Unit] =
    sendMessage(socket, "OK")

  def getMessage[F[_]: Async: Network](socket: Socket[F]): Stream[F, String] =
    Stream
      .eval(socket.read(100))
      .flatMap {
        case Some(chunk) => Stream.chunk(chunk)
        case _           => Stream.empty
      }
      .through(text.utf8.decode)

  def streamFromFile[F[_]: Async: Console: Files](path: String): Stream[F, Byte] =
    Stream.exec(Console[F].println(s"Reading from file: $path")) ++
      Files[F].readAll(Path(path))

  def sendFile[F[_]: Async: Network: Console: Files](socket: Socket[F], file: String): Stream[F, Unit] =
    streamFromFile(file)
      .through(socket.writes)

  def fileFromStream[F[_]: Concurrent: Console: Files](s: Stream[F, Byte], path: String): Stream[F, Nothing] =
    Stream.exec(Console[F].println(s"Writing file on $path")) ++
      s.through(Files[F].writeAll(Path(path)))

  def showChunks[F[_]: Async: Console, O](chunkSize: Int)(in: Stream[F, O]): Stream[F, O] =
    in.groupWithin(chunkSize, 300.millis)
      .flatMap { chunk =>
        println(chunk.size)
        println(chunk)
        Stream.chunk(chunk)
      }

  def streamPrint[F[_]: Console](message: String): Stream[F, Nothing] =
    Stream.exec(Console[F].println(message))
