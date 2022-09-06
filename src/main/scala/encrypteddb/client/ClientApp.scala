package encrypteddb.client

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.Literals.{host, port}
import com.comcast.ip4s.*

import java.io.File



object ClientApp extends IOApp:

  def pushAction = (file: String) => Client.push[IO](SocketAddress(host"localhost", port"5555"), file).compile.drain
  def getAction = (file: String) => Client.get[IO](SocketAddress(host"localhost", port"5555"), file).compile.drain


  def run(args: List[String]): IO[ExitCode] =
    (for {
      _ <- IO(new File(Client.clientFolderName).mkdirs())
      _ <- args match
        case "PUSH" :: file :: _ => pushAction(file)
        case "GET" :: file :: _ => getAction(file)
        case _ => IO(())

//      _ <- getAction
    } yield ()).as(ExitCode.Success)


