package encrypteddb.client

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.Literals.{host, port}
import com.comcast.ip4s.*
import encrypteddb.CryptoLib.setBouncyCastleProvider
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

      keySize <- IO(128)
      key     <- IO(Hex.decode("01020304050607080910111213141516"))
      keySpec <- IO(new SecretKeySpec(key, 0, keySize / 8, "AES"))
      iv      <- IO(Hex.decode("01020304050607080910111213141516"))
      ivSpec  <- IO(new IvParameterSpec(iv))
      cipher  <- IO(Cipher.getInstance("AES/CBC/PKCS5Padding", "BC"))

      client <- IO(BasicClient[IO](address))

      _ <- args match
        case "PUSH" :: file :: _ => client.push(file)
        case "GET" :: file :: _  => client.get(file)
        case _                   => IO(())
    } yield ()).as(ExitCode.Success)
