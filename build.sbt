name := "WavesFaucet"

version := "1.2-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

scalaVersion := "3.7.4"

Compile / mainClass := Some("com.wavesplatform.Faucet")

libraryDependencies ++= Seq(
  "org.apache.pekko" %% "pekko-actor-typed" % "1.4.0",
  "org.apache.pekko" %% "pekko-http"        % "1.3.0",
  "com.wavesplatform" % "node"              % "1.6.1"
)
