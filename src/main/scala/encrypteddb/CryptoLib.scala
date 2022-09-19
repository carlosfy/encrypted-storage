package encrypteddb

import cats.effect.*
import cats.implicits.*
import encrypteddb.client.Client.clientFolderName
import encrypteddb.server.Server.serverFolderName
import fs2.io.file.{Files, Path}
import fs2.{text, Chunk, Stream}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.util.encoders.Hex
import org.bouncycastle.util.io.pem.{PemObject, PemReader}

import java.io.{File, FileReader, StringWriter}
import java.security.Security
import javax.crypto.*
import javax.crypto.spec.SecretKeySpec
import scala.concurrent.duration.*

object CryptoLib:

  def setBouncyCastleProvider(): Unit =
    Security.addProvider(new BouncyCastleProvider)
    if (Security.getProvider("BC") == null)
      throw new Exception("BouncyCastle provider not installed")

  def streamFromFile[F[_]: Files](file: String): Stream[F, Byte] =
    Files[F].readAll(Path(file))

  def fileFromStream[F[_]: Files](s: Stream[F, Byte], file: String): Stream[F, Nothing] =
    s.through(Files[F].writeAll(Path(file)))

  def encryptStream[F[_]: Async](s: Stream[F, Byte], cipher: Cipher, blocksPerChunk: Int): Stream[F, Byte] =
    val chunkSize = blocksPerChunk * 8
    s.groupWithin(chunkSize, 500.millis)
      .flatMap { chunk =>
        if (chunk.size < chunkSize)
          // This only happens if it is the last chunk of the stream
          Stream.chunk(Chunk.array(cipher.doFinal(chunk.toArray)))
        else
          Stream.chunk(Chunk.array(cipher.update(chunk.toArray)))
      }

  def privateKeyToString(privateKey: SecretKeySpec): String =
    val sWrt      = new StringWriter()
    val pemWriter = new JcaPEMWriter(sWrt)

    pemWriter.writeObject(new PemObject("KEY", privateKey.getEncoded))
    pemWriter.close()

    sWrt.toString

  def getPrivateKey(file: File): SecretKeySpec =
    val keyReader = new FileReader(file)
    val pemReader = new PemReader(keyReader)

    val pemObject = pemReader.readPemObject()
    val content   = pemObject.getContent

    new SecretKeySpec(content, 0, 32, "AES")
