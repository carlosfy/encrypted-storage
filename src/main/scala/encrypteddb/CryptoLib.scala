package encrypteddb

import cats.effect.*
import cats.implicits.*
import encrypteddb.client.Client.clientFolderName
import encrypteddb.server.Server.serverFolderName
import fs2.io.file.{Files, Path}
import fs2.{Chunk, Stream}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Hex

import java.security.{SecureRandom, Security}
import javax.crypto.*
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import scala.concurrent.duration.*


object CryptoLib extends IOApp:

  def setBouncyCastleProvider(): Unit =
    Security.addProvider(new BouncyCastleProvider)
    if (Security.getProvider("BC") == null)
      throw new Exception("BouncyCastle provider not installed")

  def streamFromFile[F[_]: Files](file: String): Stream[F, Byte] =
    Files[F].readAll(Path( file))

  def fileFromStream[F[_]: Files](s: Stream[F, Byte], file: String): Stream[F, Nothing] =
    s.through(Files[F].writeAll(Path(file)))

  def encryptStream[F[_]: Async](s: Stream[F, Byte], cipher: Cipher, blocksPerChunk: Int): Stream[F, Byte] =
    val chunkSize = blocksPerChunk*8
    s.groupWithin(chunkSize, 500.millis)
      .flatMap{chunk =>
        if(chunk.size < chunkSize )
        // This only happens if it is the last chunk of the stream
          Stream.chunk(Chunk.array(cipher.doFinal(chunk.toArray)))
        else
          Stream.chunk(Chunk.array(cipher.update(chunk.toArray)))
      }


  def run(args: List[String]): IO[ExitCode] =
    for {
      _              <- IO(setBouncyCastleProvider())

      fileName       <- IO("clientFiles/meditate_monke.jpg")

      keySize        <- IO(128)
      key            <- IO(Hex.decode("01020304050607080910111213141516"))
      keySpec        <- IO(new SecretKeySpec(key, 0, keySize/8, "AES"))
      iv             <- IO(Hex.decode("01020304050607080910111213141516"))
      ivSpec         <- IO(new IvParameterSpec(iv))
      cipher         <- IO(Cipher.getInstance("AES/CBC/PKCS5Padding", "BC"))
      blocksPerChunk <- IO(128)
      _              <- IO(cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec))

      s = streamFromFile[IO](fileName)
            .through(encryptStream(_, cipher, blocksPerChunk))
            .through(fileFromStream[IO](_, "serverFiles/encryptedBouncy.jpg")) ++
            Stream.eval(IO(cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec))) ++
            streamFromFile[IO]("serverFiles/encryptedBouncy.jpg")
              .through(encryptStream(_, cipher, blocksPerChunk))
              .through(fileFromStream(_, "clientFiles/decryptedBouncy.jpg"))

        _ <- s.compile.drain
    } yield ExitCode.Success




