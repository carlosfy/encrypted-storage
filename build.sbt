ThisBuild / scalaVersion := "3.1.0"
ThisBuild / organization := "com.carlosfy"

run / fork := true

val fs2WithIO =  "co.fs2" %% "fs2-io" % "3.2.3"
val bouncyCastle = "org.bouncycastle" % "bcpkix-jdk15on" % "1.56"
val jLine = "org.jline" % "jline" % "3.12.1"

lazy val encrypted = (project in file("."))
  .settings(
    name := "EncryptedStorage",
    libraryDependencies ++= Seq(
      fs2WithIO,
      bouncyCastle,
      jLine
    )
  )

run / connectInput := true
outputStrategy := Some(StdoutOutput)