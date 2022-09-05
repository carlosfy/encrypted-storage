package encrypteddb.client

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.Literals.{host, port}
import com.comcast.ip4s.*

import java.io.File



object ClientApp extends IOApp:

  def pushAction = (file: String) => Client.push[IO](SocketAddress(host"localhost", port"5555"), file).compile.drain
  def getAction = Client.get[IO](SocketAddress(host"localhost", port"5555")).compile.drain


  def run(args: List[String]): IO[ExitCode] =
    (for {
      _ <- pushAction(args(0))
//      _ <- getAction
    } yield ()).as(ExitCode.Success)


