package encrypteddb

import cats.effect.{ExitCode, IO, IOApp}
import encrypteddb.CryptoLib.{fileFromStream, privateKeyToString, setBouncyCastleProvider, storeKey}
import fs2.{text, Stream}
import org.bouncycastle.util.encoders.Hex

import javax.crypto.spec.SecretKeySpec

object CreateKey extends IOApp.Simple:

  def run: IO[Unit] =
    for {
      _ <- IO(setBouncyCastleProvider())

      key <- IO(Hex.decode("0102030405060708091011121314151601020304050607080910111213141516"))
      keySpec <- IO(new SecretKeySpec(key, 0, 32, "AES"))

      _ <- storeKey[IO](keySpec, "pass", "key1","myKeyStore.bks" ).compile.drain

    } yield ()
