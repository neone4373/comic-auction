// import NativePackagerHelper._

name := "comic-auction"

version := "0.1.0"

lazy val akkaVersion = "2.4.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,

  
  "com.lihaoyi" %% "pprint" % "0.4.0",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "junit" % "junit" % "4.12" % "test",
  "joda-time" % "joda-time" % "2.9.3",
  "org.joda" % "joda-convert" % "1.8",
  "com.owlike" % "genson-scala_2.11" % "1.4",
  "org.scalatest" %	"scalatest-funsuite_2.11" % "3.0.0-SNAP13"
)

scalaVersion := "2.11.8"

// enablePlugins(JavaServerAppPackaging)

// mainClass in Compile := Some("sample.hello.Main")
 
testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")
 
// mappings in Universal ++= {
//   // optional example illustrating how to copy additional directory
//   directory("scripts") ++
//   // copy configuration files to config directory
//   contentOf("src/main/resources").toMap.mapValues("config/" + _)
// }
 
// add 'config' directory first in the classpath of the start script,
// an alternative is to set the config file locations via CLI parameters
// when starting the application
// scriptClasspath := Seq("../config/") ++ scriptClasspath.value
 
// licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))

