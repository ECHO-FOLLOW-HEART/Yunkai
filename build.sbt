import com.typesafe.sbt.SbtAspectj.Aspectj
import com.typesafe.sbt.SbtAspectj.AspectjKeys._

organization := "com.lvxingpai"

name := "yunkai"

version := "0.3"

scalaVersion := "2.10.4"

com.twitter.scrooge.ScroogeSBT.newSettings

//scalariformSettings

val finagleVersion = "6.14.0"

val morphiaVersion = "0.111"

val springVersion = "3.2.2.RELEASE"

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
  //  "org.slf4j" % "slf4j-log4j12" % "1.7.12",
  "com.typesafe" % "config" % "1.2.1",
  //  "com.google.code.findbugs" % "jsr305" % "3.0.0",
  "org.springframework" % "spring-aspects" % springVersion,
  "org.springframework" % "spring-aop" % springVersion,
  "org.springframework" % "spring-tx" % springVersion,
  "javax.persistence" % "persistence-api" % "1.0.2",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "org.specs2" %% "specs2-core" % "3.6",
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.10" % "2.5.2",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.5.3",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
  "net.debasishg" %% "redisclient" % "2.15",
  "org.mockito" % "mockito-all" % "2.0.2-beta",
  "org.specs2" %% "specs2-mock" % "3.6",
  "com.lvxingpai" %% "appconfig" % "0.2.1-SNAPSHOT",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.lvxingpai" %% "apium" % "0.1-SNAPSHOT",
  "commons-io" % "commons-io" % "2.4",
  "org.scalamock" %% "scalamock-scalatest-support" % "3.2" % "test"
)

publishTo := {
  val nexus = "http://nexus.lvxingpai.com/content/repositories/"
  if (isSnapshot.value)
    Some("publishSnapshots" at nexus + "snapshots")
  else
    Some("publishReleases" at nexus + "releases")
}

lazy val thriftJava = taskKey[Unit]("Generating thrift Java codes")
lazy val thriftPython = taskKey[Unit]("Generating thrift Python codes")
lazy val thriftGen = taskKey[Unit]("Generating thrift codes")
val cmdTemplate = "thrift -r -gen %s -o src/main/thrift src/main/thrift/users.thrift"

thriftJava := {
  Process(cmdTemplate format "java:beans,private-members,fullcamel") !
}

thriftPython := {
  Process(cmdTemplate format "py:utf8strings,new_style") !
}

thriftGen := {
  thriftPython.value
  thriftJava.value
}


val root = project.in(file(".")).enablePlugins(JavaAppPackaging)

scalacOptions ++= Seq("-feature", "-deprecation")

Keys.mainClass in Compile := Some("com.lvxingpai.yunkai.YunkaiServer")

parallelExecution in Test := false

fork in run := true

scalariformSettings

// AspectJ settings start

//AspectjKeys.compileOnly in Aspectj := true

aspectjSettings

AspectjKeys.showWeaveInfo in Aspectj := false


inputs in Aspectj <+= compiledClasses

binaries in Aspectj <++= update map { report =>
  report.matching(
    moduleFilter(organization = "org.springframework", name = "spring-aspects")
  )
}

binaries in Aspectj <++= update map { report =>
  report.matching(
    moduleFilter(organization = "org.springframework", name = "spring-aop")
  )
}

binaries in Aspectj <++= update map { report =>
  report.matching(
    moduleFilter(organization = "org.springframework", name = "spring-tx")
  )
}

products in Compile <<= products in Aspectj

products in Runtime <<= products in Compile

// AspectJ settings end