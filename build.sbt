name := """twitter-service"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  "com.typesafe.akka" % "akka-actor_2.11" % "2.4.16",
  "com.typesafe.akka" % "akka-slf4j_2.11" % "2.4.16"
)

