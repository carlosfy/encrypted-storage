package encrypteddb.client

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.Literals.{host, port}
import com.comcast.ip4s.*

import java.io.File



object ClientApp extends IOApp:


  def run(args: List[String]): IO[ExitCode] =
    (for {
      _ <- Client.push[IO](SocketAddress(host"localhost", port"5555"), args(0)).compile.drain
//      _ <- Client.get[IO](SocketAddress(host"localhost", port"5555")).compile.drain

    } yield ()).as(ExitCode.Success)


