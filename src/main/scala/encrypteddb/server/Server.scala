package encrypteddb.server

import fs2.io.file.{Files, Path}
import cats.effect.Concurrent
import fs2.Stream
import fs2.io.net.Network
import com.comcast.ip4s._
import cats.effect.std.Console

object Server:

    val destinationFile = "destination.jpg"
    
    def start[F[_]: Files: Network: Concurrent: Console](port: Port): Stream[F, Nothing] = 
        Stream.exec(Console[F].println(s"Listening on port: $port")) ++
        Network[F].server(port = Some(port)).map { client =>
            client.reads
                .through(Files[F].writeAll(Path(destinationFile)))
                
        }.parJoin(10)