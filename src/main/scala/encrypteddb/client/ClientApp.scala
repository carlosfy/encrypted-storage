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

  def run(args: List[String]): IO[ExitCode] =
    Console.create[IO].flatMap { implicit console =>
      for {
        _ <- IO(new File(Client.clientFolderName).mkdirs())
        _ <- IO(setBouncyCastleProvider())

        address <- IO(SocketAddress(host"localhost", port"5555"))
        keySpec <- IO(getKeyFromFile("key1", "pass", "myKeyStore.bks"))

        iv     <- IO(Hex.decode("01020304050607080910111213141516"))
        ivSpec <- IO(new IvParameterSpec(iv))
        cipher <- IO(Cipher.getInstance("AES/CBC/PKCS5Padding", "BC"))

        //      client <- IO(BasicClient[IO](address))
        //      client <- IO(DebugClient[IO](address, 20))
        client <- IO(EncryptedClient[IO](address, cipher, keySpec, ivSpec))

        _ <- client.start

      } yield ExitCode.Success

    }
