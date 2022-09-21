# Encrypted Storage

This repository implements a TCP based encrypted storage server using fs2 and BouncyCastle.

## Usage

To create the KeyStore and/or add a key to it, run `sbt "runMain encrypteddb.CreateKey"`. Note that this script will ask you for an username and a password, and you will need this credentials to create a client that encrypt the files.

To start the server application that listen TCP connections on port 5555 run `sbt "runMain encrypteddb.server.ServerApp"`.

To start the client application run `sbt "runMain encrypteddb.client.ClientApp"`. Note that if you want to use the encrypted mode you will need to run CreateKey first in order to create the KeyStore and a load a key on it.
Before creating the client, you will be asked if you want to encrypt your files, if you say "yes" you will have to provide user/password. Otherwise, you are good to go.
Once the client is created, you can either:
- Push a file from your local folder("/clientFiles") to the server folder("/serverFiles") by writing "PUSH <fileName>
- Get a file from the server by writing "GET <fileName>"

## Improvements to be made

- Add commands:
    - Delete file
    - Scan: to get the list of files available
    - Put, to override (fail on push)
- The application it is not safe e.g: 
  - A client can get any client of the server e.g. "GET ../myKeyStore.bks" or even override "PUSH ../src/main/scala/encrypteddb/server/Server.scala".
- Many parts of this code do not respect the functional programming principles, e.g. the cipher is not immutable, the calls to encrypt are not referentially transparent. I would be good to implemented in a FP way.
- Client can override other users files, we should a system for managing rights and nameespaces, e.g. using database
- Error handling is very poor.
- The protocol should be improved and use common scala classes, this would help to error handling.
- Random salt and iv should be used
- There should be tests, e.g. one pushing, deleting a file and the getting it back again.
- Libs depend on each other: CommonMethods depends on Console, it should not be the case, it would be better to just use the Console of cats
- Performance of CreateKey could be improved: throw errors before doing the heave stuff e.g. RandomSecure()
