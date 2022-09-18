ThisBuild / scalaVersion := "3.1.0"
ThisBuild / organization := "com.carlosfy"

run / fork := true

val fs2WithIO =  "co.fs2" %% "fs2-io" % "3.2.3"
val bouncyCastle = "org.bouncycastle" % "bcpkix-jdk15on" % "1.56"

lazy val encrypted = (project in file("."))
  .settings(
    name := "EncriptedStorage",
    libraryDependencies ++= Seq(
      fs2WithIO,
      bouncyCastle
    )
  )

connectInput / run := true



