package encrypteddb.client

import cats.effect.Temporal
import cats.effect.std.Console
import fs2.io.net.Network
import fs2.{Stream, text}
import com.comcast.ip4s.*
import fs2.io.file.{Files, Path}

object Client:


    val sourceFile = "/home/carlos/dev/tuto/blindnet/challenge/medidate_monke.jpg"

    def start[F[_]: Temporal: Network: Console: Files](address: SocketAddress[Host], source: String): Stream[F, Unit] =
        Stream.exec(Console[F].println(s"Trying to connect to $address")) ++
          Stream
            .resource(Network[F].client(address))
            .flatMap { socket =>
                Stream.exec(Console[F].println(s"Connected to $address")) ++
                  Files[F].readAll(Path(source))
                    .through(socket.writes)
            }
