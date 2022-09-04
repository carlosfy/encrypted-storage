package encrypteddb.server

import fs2.io.file.{Files, Path}
import cats.effect.Concurrent
import fs2.{Stream, text}
import fs2.io.net.Network
import com.comcast.ip4s.*
import cats.effect.std.Console
import cats.MonadError

object Server:

    val destinationFile = "destination.jpg"
    
    def start[F[_]: Files: Network: Concurrent: Console](port: Port): Stream[F, Nothing] = 
        Stream.exec(Console[F].println(s"Listening on port: $port")) ++
        Network[F].server(port = Some(port)).map { client =>
            client.reads
                .through(Files[F].writeAll(Path(destinationFile)))
                
        }.parJoin(10)


    def startCommands[F[_]: Files: Network: Concurrent: Console](port: Port): Stream[F, Nothing] =
        Stream.exec(Console[F].println(s"Listening on port: $port")) ++
          Network[F].server(port = Some(port)).map { client =>
            client.reads
              .through(text.utf8.decode)
              .through(text.lines)
              .head
              .flatMap {resp => resp match
                  case "PUSH" => Stream("Ok").interleave(Stream.constant("\n")).through(text.utf8.encode).through(client.writes)
                  case s => Stream.eval(Console[F].println(s"unknown command: $s")) >> Stream.raiseError(new Error("Unknown command"))
              } ++
              client.reads
                .through(Files[F].writeAll(Path(destinationFile)))

              }.parJoin(10)
