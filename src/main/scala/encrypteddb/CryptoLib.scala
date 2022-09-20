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
import sun.security.util.Password

import java.io.{ByteArrayOutputStream, File, FileInputStream, FileReader, InputStream, StringWriter}
import java.security.{KeyStore, Security}
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

  def fromKeyToKeyStore(key: SecretKeySpec, alias: String, password: String, fileName: String): Array[Byte] =
    val keyStoreFile = new File(fileName)
    val keyStore     = KeyStore.getInstance("BCFKS", "BC")
    if (keyStoreFile.exists())
      val inputStream: InputStream = new FileInputStream(keyStoreFile)
      keyStore.load(inputStream, "keyStoreKey".toCharArray)
    else keyStore.load(null, null)
    if (keyStore.containsAlias(alias)) throw Error(s"The username $alias already exists")
    else
      keyStore.setKeyEntry(alias, key, password.toCharArray, null)
      val bOut = new ByteArrayOutputStream()
      keyStore.store(bOut, "keyStoreKey".toCharArray)
      bOut.toByteArray

  def storeKey[F[_]: Files](key: SecretKeySpec, password: String, alias: String, fileName: String): Stream[F, Nothing] =
    Stream
      .chunk(Chunk.array(fromKeyToKeyStore(key, alias, password, fileName)))
      .through(Files[F].writeAll(Path(fileName)))

  def getKeyFromFile(alias: String, password: String, file: String): SecretKeySpec =
    val keyStoreFile             = new File(file)
    val inputStream: InputStream = new FileInputStream(keyStoreFile)
    val keyStore                 = KeyStore.getInstance("BCFKS", "BC")
    keyStore.load(inputStream, "keyStoreKey".toCharArray)
    val key = keyStore.getKey(alias, password.toCharArray)
    new SecretKeySpec(key.getEncoded, "AES")
