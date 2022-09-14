package encrypteddb
package client

import cats.effect.{Async, Concurrent, MonadCancel}
import cats.effect.std.Console
import fs2.io.net.{Network, Socket}
import fs2.{Chunk, Pipe, Stream, text}
import com.comcast.ip4s.*
import fs2.io.file.{Files, Path}
import cats.syntax.all.*
import encrypteddb.CommunMethods.{fileFromStream, getMessage, sendMessage, streamFromFile}
import encrypteddb.CryptoLib.encryptStream
import encrypteddb.client.Client.{clientFolderName, connect, getValidatedResponse}

import java.io.FileNotFoundException
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import javax.crypto.{Cipher, SecretKey}

abstract class Client[F[_]: Async: Network: Console: Files](address: SocketAddress[Host]):
  import Client._

  def push(file: String): F[Unit] =
    (Stream.exec(Console[F].println(s"Trying to push file $file to $address")) ++
      connect(address)
        .flatMap { socket =>
          sendMessage(socket, "PUSH" + " " + file) ++
            getValidatedResponse(socket) ++
            streamFromFile(clientFolderName + file)
              .through(pushStream(_, socket)) ++
            Stream.exec(Console[F].println(s"Pushing data done"))
        }).compile.drain

  def get(file: String): F[Unit] =
    (Stream.exec(Console[F].println(s"Trying to get a file from $address")) ++
      connect(address)
        .flatMap { socket =>
          sendMessage(socket, "GET" + " " + file) ++
            Stream.exec(Console[F].println(s"Receiving data from $address")) ++
            getValidatedResponse(socket) ++
            getStream(socket)
              .through(fileFromStream(_, clientFolderName + file)) ++
            Stream.exec(Console[F].println(s"Receive data done"))
        }).compile.drain

  def pushStream(stream: Stream[F, Byte], socket: Socket[F]): Stream[F, Nothing]
  def getStream(socket: Socket[F]): Stream[F, Byte]

case class BasicClient[F[_]: Async: Network: Console: Files](address: SocketAddress[Host])
    extends Client[F](address) {
  import Client._

  def pushStream(stream: Stream[F, Byte], socket: Socket[F]): Stream[F, Nothing] =
    stream.through(socket.writes)

  def getStream(socket: Socket[F]): Stream[F, Byte] =
    socket.reads
}

case class EncryptedClient[F[_]: Async: Network: Console: Files](address: SocketAddress[Host], cipher: Cipher, key: SecretKeySpec, iv: IvParameterSpec)
  extends Client[F](address) {
  import Client._

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

  def connect[F[_]: Concurrent: Network: Console](address: SocketAddress[Host]): Stream[F, Socket[F]] =
    Stream.exec(Console[F].println(s"Trying to connect to $address")) ++
      Stream.resource(Network[F].client(address))

  def validateResponse[F[_]: Concurrent: Network: Console: Files](response: String): Stream[F, Nothing] =
    response.toUpperCase() match
      case "OK" => Stream.empty
      case _    => Stream.raiseError(InvalidServerResponse(s"Reponse: $response"))

  def getValidatedResponse[F[_]: Concurrent: Network: Console: Files](socket: Socket[F]): Stream[F, Nothing] =
    getMessage(socket)
      .flatMap(validateResponse(_))
