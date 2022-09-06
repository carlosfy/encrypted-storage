package encrypteddb

import cats.effect.Concurrent
import cats.effect.std.Console
import fs2.io.file.Files
import fs2.io.net.{Network, Socket}
import fs2.{Stream, text}

object CommunMethods:

  def sendMessage[F[_]: Network](socket: Socket[F], command: String): Stream[F, Nothing] =
    Stream(command)
      .interleave(Stream.constant("\n"))
      .through(text.utf8.encode)
      .through(socket.writes)


  def getMessage[F[_]: Concurrent: Network: Console: Files]( socket: Socket[F]): Stream[F, String] =
    socket.reads
      .through(text.utf8.decode)
      .through(text.lines)
      .head

  

