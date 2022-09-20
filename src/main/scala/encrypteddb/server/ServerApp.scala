package encrypteddb.server

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*
import encrypteddb.Console

import java.io.File

object ServerApp extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    (for {
      _ <- IO(new File(Server.serverFolderName).mkdirs())
      _ <- Console
        .create[IO]
        .flatMap { implicit consoleReader =>
          Server.startController[IO](port"5555").compile.drain
        }
    } yield ()).as(ExitCode.Success)
