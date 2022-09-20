# Encrypted Storage

This repository implements a TCP based encrypted storage server using fs2 and BouncyCastle.

## Usage

To create the KeyStore and/or add a key to it, run `sbt "runMain encrypteddb.CreateKey"`. Note that this script will ask you for an username and a password, and you will need this credentials to create a client that encrypt the files.

To start the server application that listen TCP connections on port 5555 run `sbt "runMain encrypteddb.server.ServerApp"`.

To start the client application run `sbt "runMain encrypteddb.client.ClientApp"`. Note that if you want to use the encrypted mode you will need to run CreateKey first in order to create the KeyStore and a load a key on it.

