organization := "com.lvxingpai"

name := "yunkai"

version := "0.3"

scalaVersion := "2.10.4"

com.twitter.scrooge.ScroogeSBT.newSettings

scalariformSettings

val finagleVersion = "6.14.0"

val morphiaVersion = "0.111"

libraryDependencies ++= Seq(
  "org.mongodb" % "mongo-java-driver" % "3.0.0",
  "org.mongodb.morphia" % "morphia" % morphiaVersion,
  "org.mongodb.morphia" % "morphia-validation" % morphiaVersion,
  "org.mongodb.morphia" % "morphia-util" % morphiaVersion,
  "org.hibernate" % "hibernate-validator" % "5.1.3.Final",
  "javax.el" % "javax.el-api" % "3.0.0",
  "org.glassfish.web" % "javax.el" % "2.2.6",
  "cglib" % "cglib-nodep" % "3.1",
  "com.thoughtworks.proxytoys" % "proxytoys" % "1.0",
  "com.twitter" %% "finagle-core" % finagleVersion,
  "com.twitter" %% "finagle-thrift" % finagleVersion,
  "com.twitter" %% "finagle-thriftmux" % finagleVersion,
  "com.twitter" %% "scrooge-core" % "3.18.1",
  "org.apache.thrift" % "libthrift" % "0.9.2",
  "org.slf4j" % "slf4j-log4j12" % "1.7.12",
  "com.typesafe" % "config" % "1.2.1",
  //  "com.google.code.findbugs" % "jsr305" % "3.0.0",
  "org.specs2" %% "specs2-core" % "3.6",
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.10" % "2.5.2",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.5.3",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
  "net.debasishg" %% "redisclient" % "2.15",
  "org.mockito" % "mockito-all" % "2.0.2-beta",
  "org.specs2" %% "specs2-mock" % "3.6",
  "com.lvxingpai" %% "appconfig" % "0.1.2-SNAPSHOT",
  "com.lvxingpai" %% "apium" % "0.1-SNAPSHOT"
)

publishTo := {
  val nexus = "http://nexus.lvxingpai.com/content/repositories/"
  if (isSnapshot.value)
    Some("publishSnapshots" at nexus + "snapshots")
  else
    Some("publishReleases" at nexus + "releases")
}

val root = project.in(file(".")).enablePlugins(JavaAppPackaging)

scalacOptions ++= Seq("-feature", "-deprecation")

Keys.mainClass in Compile := Some("com.lvxingpai.yunkai.YunkaiServer")

fork in run := true
