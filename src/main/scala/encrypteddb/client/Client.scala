package encrypteddb
package client

import cats.effect.{Async, Concurrent, MonadCancel}
import cats.syntax.all.*
import com.comcast.ip4s.*
import encrypteddb.CommonMethods.*
import encrypteddb.Console
import encrypteddb.CryptoLib.encryptStream
import fs2.io.file.{Files, Path}
import fs2.io.net.{Network, Socket}
import fs2.{Chunk, Pipe, Stream}

import java.io.FileNotFoundException
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import javax.crypto.{Cipher, SecretKey}

abstract class Client[F[_]: Async: Network: Console: Files](address: SocketAddress[Host]):
  import Client.*

  def start: F[Unit] =
    for {
      _ <- handleCommand
      _ <- start
    } yield ()

  def handleCommand: F[Unit] =
    for {
      command <- (Console[F].readLine("Enter Command > "))
      _ <- command.getOrElse("").split(" ").toList match
        case "PUSH" :: (file: String) :: _ => push(file)
        case "GET" :: (file: String) :: _  => get(file)
        case _                             => Async[F].pure(())
    } yield ()

  def push(file: String): F[Unit] =
    (streamPrint(s"Trying to push file $file to $address") ++
      connect(address)
        .flatMap { socket =>
          sendMessage(socket, "PUSH" + " " + file) ++
            getValidatedResponse(socket) ++
            streamFromFile(clientFolderName + file)
              .through(pushStream(_, socket)) ++
            streamPrint(s"Pushing file done")
        }).compile.drain

  def get(file: String): F[Unit] =
    (streamPrint(s"Trying to get a file from $address") ++
      connect(address)
        .flatMap { socket =>
          sendMessage(socket, "GET" + " " + file) ++
            streamPrint(s"Waiting for response $address") ++
            getValidatedResponse(socket) ++
            sendOk(socket) ++
            streamPrint(s"Receiving data from $address") ++
            getStream(socket)
              .through(fileFromStream(_, clientFolderName + file)) ++
            streamPrint(s"Receive file done")
        }).compile.drain

  def pushStream(stream: Stream[F, Byte], socket: Socket[F]): Stream[F, Nothing]
  def getStream(socket: Socket[F]): Stream[F, Byte]

case class BasicClient[F[_]: Async: Network: Console: Files](address: SocketAddress[Host]) extends Client[F](address) {
  import Client.*

  def pushStream(stream: Stream[F, Byte], socket: Socket[F]): Stream[F, Nothing] =
    stream
      .through(socket.writes)

  def getStream(socket: Socket[F]): Stream[F, Byte] =
    socket.reads
}

case class DebugClient[F[_]: Async: Network: Console: Files](address: SocketAddress[Host], chunkSize: Int)
    extends Client[F](address) {
  import Client.*

  def pushStream(stream: Stream[F, Byte], socket: Socket[F]): Stream[F, Nothing] =
    stream
      .through(showChunks(chunkSize))
      .through(socket.writes)

  def getStream(socket: Socket[F]): Stream[F, Byte] =
    socket.reads
      .through(showChunks(chunkSize))
}

case class EncryptedClient[F[_]: Async: Network: Console: Files](
    address: SocketAddress[Host],
    cipher: Cipher,
    key: SecretKeySpec,
    iv: IvParameterSpec
) extends Client[F](address) {
  import Client.*

  def pushStream(stream: Stream[F, Byte], socket: Socket[F]): Stream[F, Nothing] =
    cipher.init(Cipher.ENCRYPT_MODE, key, iv) // Not FP
    stream
      .through(encryptStream[F](_, cipher, 128))
      .through(socket.writes)

  def getStream(socket: Socket[F]): Stream[F, Byte] =
    cipher.init(Cipher.DECRYPT_MODE, key, iv) // Not FP
    socket.reads
      .through(encryptStream[F](_, cipher, 128))

}

object Client:

  def clientFolderName = "clientFiles/"

  case class InvalidServerResponse(str: String) extends Exception(str)

  def connect[F[_]: Async: Network: Console](address: SocketAddress[Host]): Stream[F, Socket[F]] =
    Stream.exec(Console[F].println(s"Trying to connect to $address")) ++
      Stream.resource(Network[F].client(address))

  def validateResponse[F[_]: Async: Network: Console: Files](response: String): Stream[F, Unit] =
    response.toUpperCase() match
      case "OK" => Stream.eval(Async[F].pure(()))
      case _    => Stream.raiseError(InvalidServerResponse(s"Reponse: $response"))

  def getValidatedResponse[F[_]: Async: Network: Console: Files](socket: Socket[F]): Stream[F, Unit] =
    getMessage(socket)
      .flatMap(validateResponse(_))
