package encrypteddb

import cats.effect.kernel.Concurrent
import cats.effect.kernel.Resource.Pure
import cats.effect.std.Console
import cats.effect.{ExitCode, IO, IOApp}
import encrypteddb.CryptoLib.keyGenerator
import fs2.{Chunk, Pipe, Pull, Stream, text}

import javax.crypto.*
import cats.implicits.*
import encrypteddb.client.Client.clientFolderName
import encrypteddb.server.Server.serverFolderName
import fs2.io.file.{Files, Path}

import java.security.SecureRandom
import java.util.Base64

object CryptoLib extends IOApp:

  // Stream.scanChunkOpt
  //      _ <- getAction

  val message1 = "123".getBytes()
  val message2 = "45678".getBytes()
  val message3 = "9!".getBytes()

  println(s"Bytes of the message1: ${message1.size}")
  println(s"Bytes of the message2: ${message2.size}")
  println(s"Bytes of the message3: ${message3.size}")

  val keyGenerator = KeyGenerator.getInstance("DES")
  val desKey = keyGenerator.generateKey()

  val desCipher = Cipher.getInstance("DES/ECB/PKCS5Padding") //AES/ECB/PKCS5Padding - "DES/ECB/NoPadding"
  desCipher.init(Cipher.ENCRYPT_MODE, desKey)

  val encrypted1 = desCipher.doFinal(message1)
  val encrypted2 = desCipher.doFinal(message2)
  val encrypted3 = desCipher.doFinal(message3)

  println(s"Bytes of the encrypted1: ${encrypted1.size}")
  println(s"encrypted1 = ${java.util.Arrays.toString(encrypted1)}")
  println(s"Bytes of the encrypted2: ${encrypted2.size}")
  println(s"encrypted2 = ${java.util.Arrays.toString(encrypted2)}")
  println(s"Bytes of the encrypted3: ${encrypted3.size}")
  println(s"encrypted3 = ${java.util.Arrays.toString(encrypted3)}")

  val totalPlainBytes = message1 ++ message2 ++ message3
  val encryptedOnce = desCipher.doFinal(totalPlainBytes)

  println(s"Bytes of the EncryptedOnce: ${encryptedOnce.size}")
  println(s"EncryptedOnce = ${java.util.Arrays.toString(encryptedOnce)}")


//  val plainText = "1234567"//"abcdefghijklmnopqrstuvwxyz"
//  val plainData = plainText.getBytes("UTF-8")
//
//  println(s"arrays size ${plainData.size}")
//  println(s"array : ${plainData.foreach(println(_))}")
//
//  println(s"Initial text: $plainText")
//
//  val cipherData = desCipher.doFinal(plainData)
//  val cipherText = String(cipherData, "UTF-8")

//  println(s"Cipher text: $cipherText")
//
//  println(desCipher)

  desCipher.init(Cipher.DECRYPT_MODE, desKey)

//  val recovered1 = desCipher.doFinal(encrypted1)
//  val recovered2 = desCipher.doFinal(encrypted2)
//
//  val recoveredText1 = String(recovered1, "UTF-8")
//  val recoveredText2 = String(recovered2, "UTF-8")

  val totalBytes = encrypted1 ++ encrypted2 ++ encrypted3

  println(s"Bytes of the totalBytes: ${totalBytes.size}")
  println(s"totalBytes = ${java.util.Arrays.toString(totalBytes)}")


  val recoveredTotal = desCipher.doFinal(totalBytes)

  println(s"Bytes of the recoveredTotal: ${totalBytes.size}")
  println(s"recoveredTotal = ${java.util.Arrays.toString(totalBytes)}")

  val recoveredText = String(recoveredTotal, "UTF-8")


  val recoveredOnce = desCipher.doFinal(encryptedOnce)

  println(s"Bytes of the recoveredOnce: ${totalBytes.size}")
  println(s"recoveredOnce = ${java.util.Arrays.toString(totalBytes)}")
  val recoveredOnceText = String(recoveredOnce, "UTF-8")



  //  val recoveredData = desCipher.doFinal(cipherData)
//  val recoveredText = String(recoveredData, "UTF-8")

  println(s"Recovered text: ${ recoveredText}")
  println(s"Recovered text at once: ${ recoveredOnceText}")


  def toStream[F[_]: Concurrent](s: String): F[Stream[F, Byte]] =
    (s.pure[F]).map(r => Stream.emits(r.getBytes))


  def encryptStream[F[_]](s: Stream[F, Byte], cipherECB: Cipher, key: SecretKey): Stream[F, Byte] =
    cipherECB.init(Cipher.ENCRYPT_MODE, key)
    s
      .mapChunks{
        chunk =>
          println(s"Chunk size before encrypt: ${chunk.size}, ${chunk.toArray.foreach(println(_))}")
          val myArray = chunk.toArray
//          println(s"${java.util.Arrays.toString(myArray)} array size: ${java.util.Arrays.toString(myArray)} ${java.util.Arrays.toString(plainData)}")
//          println(s"Both arrays are the same: ${myArray == plainData}")
          Chunk.array((cipherECB.doFinal(chunk.toArray)))
      }

  def decryptStream[F[_]](s: Stream[F, Byte], cipherECB: Cipher, key: SecretKey): Stream[F, Byte] =
    cipherECB.init(Cipher.DECRYPT_MODE, key)
    s
      .mapChunks{
        chunk =>
          println(s"Chunk size before decrypt: ${chunk.size}, ${chunk.toArray}")
          Chunk.array((cipherECB.doFinal(chunk.toArray)))
      }

  def cryptStream[F[_]: Console](s: Stream[F, Byte], cipherECB: Cipher, key: SecretKey, mode: Int): Stream[F, Byte] =
    cipherECB.init(mode, key)
    s
      .mapChunks{ chunk =>
        println(s"Chunk size before encrypting: ${chunk.size}")
        Chunk.array((cipherECB.doFinal(chunk.toArray)))
      }

  def printingChunkSize[F[_]: Console, O](s: Stream[F, O]): Stream[F, O] =
    s.mapChunks{ chunk =>
      println(s"Chunk size: ${chunk.size}")
      println(chunk)
      chunk
    }

  def streamFromFile[F[_]: Files](file: String): Stream[F, Byte] =
    Files[F].readAll(Path( file))

  def fileFromStream[F[_]: Files](s: Stream[F, Byte], file: String): Stream[F, Nothing] =
    s.through(Files[F].writeAll(Path( file)))


  def run(args: List[String]): IO[ExitCode] =

    for{
      keyGenerator <- IO(KeyGenerator.getInstance("AES"))
      key <- IO(keyGenerator.generateKey())
      cipher <- IO(Cipher.getInstance("AES/ECB/PKCS5Padding"))
      _ <- IO(cipher.init(Cipher.ENCRYPT_MODE, key ))
      _ <- (streamFromFile[IO]("clientFiles/input.txt")
        .chunks
        .flatMap[IO, Byte] { chunk =>
          Stream.chunk(Chunk.array(cipher.doFinal(chunk.toArray)))
        }.through(fileFromStream[IO](_, "serverFiles/encrypted.txt")) ++
        Stream.eval(IO(cipher.init(Cipher.DECRYPT_MODE, key))) ++
        streamFromFile[IO]("serverFiles/encrypted.txt")
          .chunks
          .flatMap{chunk =>
            Stream.chunk(Chunk.array(cipher.doFinal(chunk.toArray)))
          }
          .chunks
          .map {
            a => String(a.toArray, "UTF-8")
          }
          .foreach(IO.println(_))
        )
          .compile.drain
    } yield ExitCode.Success





