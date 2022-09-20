package encrypteddb.client

import cats.effect.{Async, ExitCode, IO, IOApp}
import com.comcast.ip4s.Literals.{host, port}
import com.comcast.ip4s.*
import encrypteddb.Console
import encrypteddb.CryptoLib.{getKeyFromFile, getPrivateKey, setBouncyCastleProvider}
import org.bouncycastle.util.encoders.Hex
import cats.implicits.*

import java.io.File
import java.nio.file.NoSuchFileException
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}

/**
 * Script for launching a client
 */
object ClientApp extends IOApp:

  // I didn't refactor this 2 methods so the lib does not depend on Console
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

  def tryOpenKeyStore[F[_]: Console: Async]: F[SecretKeySpec] =
    for {
      user     <- Console[F].readLine("Username? ").map(_.getOrElse(""))
      _        <- Console[F].println(s"Username: $user")
      password <- Console[F].readLine("Password? ").map(_.getOrElse(""))
      key      <- Async[F].pure(getKeyFromFile(user, password, "myKeyStore.bks"))
    } yield (key)

  def run(args: List[String]): IO[ExitCode] =
    Console.create[IO].flatMap { implicit console =>
      for {
        _ <- IO(new File(Client.clientFolderName).mkdirs()) // Create clientFiles if it doesn't exist
        _ <- IO(setBouncyCastleProvider())                  // Set BouncyCastle as a client

        address <- IO(SocketAddress(host"localhost", port"5555"))

        iv     <- IO(Hex.decode("01020304050607080910111213141516"))
        ivSpec <- IO(new IvParameterSpec(iv))
        cipher <- IO(Cipher.getInstance("AES/CBC/PKCS5Padding", "BC"))

        client <- createClient(address, ivSpec, cipher)

        _ <- client.start.handleErrorWith {
          case err: NoSuchFileException => console.println(s"ERROR: $err") >> client.start
          case err                      => IO.raiseError(err)

        }

      } yield ExitCode.Success

    }
