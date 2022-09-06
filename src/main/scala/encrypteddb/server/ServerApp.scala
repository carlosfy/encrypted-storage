package encrypteddb.server

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*

import java.io.File

object ServerApp extends IOApp:

    def startListening = Server.startController[IO](port"5555").compile.drain

    def run(args: List[String]): IO[ExitCode] =
        (for {
            _ <- IO(new File(Server.clientFolderName).mkdirs())
            _ <- startListening
        } yield ()).as(ExitCode.Success)
