name := "datadog"

version := "0.1"

scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  "com.twitter" %% "util-core" % "19.1.0",
  "com.typesafe.akka" %% "akka-stream" % "2.5.21",
  "org.rogach" %% "scallop" % "3.1.5",
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "org.scalacheck" %% "scalacheck" % "1.14.0" % Test,
  "org.mockito" % "mockito-core" % "2.24.5" % Test
)

enablePlugins(DockerPlugin)
enablePlugins(JavaAppPackaging)

// Uncomment to disable tests
// test in assembly := {}

target in assembly := file(".")
