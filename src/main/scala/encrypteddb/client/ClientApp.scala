package encrypteddb.client

import cats.effect.{Async, ExitCode, IO, IOApp}
import com.comcast.ip4s.Literals.{host, port}
import com.comcast.ip4s.*
import encrypteddb.Console
import encrypteddb.CryptoLib.{getKeyFromFile, getPrivateKey, setBouncyCastleProvider}
import org.bouncycastle.util.encoders.Hex
import cats.implicits._

import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}

object ClientApp extends IOApp:

  def tryOpenKeyStore[F[_]: Console: Async]: F[SecretKeySpec] =
    for {
      user     <- Console[F].readLine("Username? ").map(_.getOrElse(""))
      _        <- Console[F].println(s"Username: $user")
      password <- Console[F].readLine("Password? ").map(_.getOrElse(""))
      key      <- Async[F].pure(getKeyFromFile(user, password, "myKeyStore.bks"))
    } yield (key)

  def createClient[F[_]: Console: Async](
      address: SocketAddress[Hostname],
      iv: IvParameterSpec,
      cipher: Cipher
  ): F[Client[F]] =
    for {
      answer <- Console[F].readLine("Do you want to encrypt your files? ")
      client <- answer.getOrElse("") match
        case "yes" =>
          tryOpenKeyStore[F]
            .flatTap(_ => Console[F].println("Encrypted client created"))
            .map(key => EncryptedClient(address, cipher, key, iv))
            .handleErrorWith(err => Console[F].println(err.toString) >> createClient[F](address, iv, cipher))
        case "no" => Console[F].println("Basic client created").as(BasicClient[F](address))
        case res  => Console[F].println(s"Unknown response: $res") >> createClient(address, iv, cipher)
    } yield client

  def run(args: List[String]): IO[ExitCode] =
    Console.create[IO].flatMap { implicit console =>
      for {
        _ <- IO(new File(Client.clientFolderName).mkdirs())
        _ <- IO(setBouncyCastleProvider())

        address <- IO(SocketAddress(host"localhost", port"5555"))

        iv     <- IO(Hex.decode("01020304050607080910111213141516"))
        ivSpec <- IO(new IvParameterSpec(iv))
        cipher <- IO(Cipher.getInstance("AES/CBC/PKCS5Padding", "BC"))

        //      client <- IO(BasicClient[IO](address))
        //      client <- IO(DebugClient[IO](address, 20))
        client <- createClient(address, ivSpec, cipher)

        _ <- client.start.handleErrorWith(err => IO.raiseError(err))

      } yield ExitCode.Success

    }
