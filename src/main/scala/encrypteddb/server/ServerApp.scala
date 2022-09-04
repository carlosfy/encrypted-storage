package encrypteddb.server

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s._

object ServerApp extends IOApp: 
    def run(args: List[String]): IO[ExitCode] =
        Server.start[IO](port"5555").compile.drain.as(ExitCode.Success)