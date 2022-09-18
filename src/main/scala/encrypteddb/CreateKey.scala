package encrypteddb

import cats.effect.{ExitCode, IO, IOApp}
import encrypteddb.CryptoLib.{fileFromStream, privateKeyToString, setBouncyCastleProvider}
import fs2.{text, Stream}
import org.bouncycastle.util.encoders.Hex

import javax.crypto.spec.SecretKeySpec

object CreateKey extends IOApp.Simple:

  def run: IO[Unit] =
    for {
      _ <- IO(setBouncyCastleProvider())

      _ <- Stream
        .eval(IO(Hex.decode("0102030405060708091011121314151601020304050607080910111213141516")))
        .map(new SecretKeySpec(_, 0, 32, "AES"))
        .map(privateKeyToString)
        .through(text.utf8.encode)
        .through(fileFromStream(_, "src/main/resources/client/savedKey.pem"))
        .compile
        .drain
    } yield ()
