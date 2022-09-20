package encrypteddb

import cats.effect.{ExitCode, IO, IOApp}
import encrypteddb.CryptoLib.{fileFromStream, privateKeyToString, setBouncyCastleProvider, storeKey}
import fs2.{text, Stream}
import org.bouncycastle.util.encoders.Hex

import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

object CreateKey extends IOApp.Simple:

  def run: IO[Unit] =
    Console.create[IO].flatMap { implicit console =>
      for {
        _ <- IO(setBouncyCastleProvider())

        user     <- console.readLine("Enter your username: ").map(_.getOrElse(""))
        _        <- IO(if (user == "") throw Error("You must enter an username"))
        password <- console.readLine("Enter your password: ").map(_.getOrElse(""))
        _        <- IO(if (user == "") throw Error("You must enter an UserName"))

        random <- IO(new SecureRandom())
        bytes  <- IO(Hex.decode("0102030405060708091011121314151601020304050607080910111213141516"))
        _      <- IO(random.nextBytes(bytes))

        keySpec <- IO(new SecretKeySpec(bytes, 0, 32, "AES"))

        _ <- storeKey[IO](keySpec, password, user, "myKeyStore.bks").compile.drain

      } yield ()
    }
