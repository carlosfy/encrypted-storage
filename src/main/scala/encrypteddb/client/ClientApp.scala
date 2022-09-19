package encrypteddb.client

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.Literals.{host, port}
import com.comcast.ip4s.*
import encrypteddb.Console
import encrypteddb.CryptoLib.{getPrivateKey, setBouncyCastleProvider}
import org.bouncycastle.util.encoders.Hex

import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}

object ClientApp extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    (for {
      _ <- IO(new File(Client.clientFolderName).mkdirs())
      _ <- IO(setBouncyCastleProvider())

      address <- IO(SocketAddress(host"localhost", port"5555"))

      keySpec <- IO(getPrivateKey(new File("src/main/resources/client/savedKey.pem")))

      iv     <- IO(Hex.decode("01020304050607080910111213141516"))
      ivSpec <- IO(new IvParameterSpec(iv))
      cipher <- IO(Cipher.getInstance("AES/CBC/PKCS5Padding", "BC"))

//      client <- IO(BasicClient[IO](address))
//      client <- IO(DebugClient[IO](address, 20))
//      client <- EncryptedClient[IO](address, cipher, keySpec, ivSpec)

      _ <- Console.create[IO]
        .flatMap{ implicit consoleReader =>
          EncryptedClient[IO](address, cipher, keySpec, ivSpec).start
        }

    } yield ()).as(ExitCode.Success)
