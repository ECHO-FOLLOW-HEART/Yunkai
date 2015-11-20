import com.typesafe.sbt.SbtAspectj.Aspectj
import com.typesafe.sbt.SbtAspectj.AspectjKeys._

organization := "com.lvxingpai"

name := "yunkai"

version := "0.5"

scalaVersion := "2.11.4"

com.twitter.scrooge.ScroogeSBT.newSettings

val springVersion = "3.2.2.RELEASE"

libraryDependencies ++= Seq(
  "com.lvxingpai" %% "etcd-store-guice" % "0.1.1-SNAPSHOT",
  "com.lvxingpai" %% "morphia-guice" % "0.1.0-SNAPSHOT",
  "com.lvxingpai" %% "configuration" % "0.1.1",
  "com.twitter" %% "finagle-thriftmux" % "6.30.0",
  "com.twitter" %% "scrooge-core" % "4.2.0",
  "org.hibernate" % "hibernate-validator" % "5.1.3.Final",
  "net.debasishg" %% "redisclient" % "2.15",
  "javax.el" % "javax.el-api" % "3.0.0",
  "org.glassfish.web" % "javax.el" % "2.2.6",
  "cglib" % "cglib-nodep" % "3.1",
  "com.thoughtworks.proxytoys" % "proxytoys" % "1.0",
  "org.springframework" % "spring-aspects" % springVersion,
  "org.springframework" % "spring-aop" % springVersion,
  "org.springframework" % "spring-tx" % springVersion,
  "javax.persistence" % "persistence-api" % "1.0.2",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "org.specs2" %% "specs2-core" % "3.6",
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.11" % "2.6.3",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.6.3",
  "org.mockito" % "mockito-all" % "2.0.2-beta",
  "org.specs2" %% "specs2-mock" % "3.6",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.lvxingpai" %% "apium" % "0.1-SNAPSHOT",
  "commons-io" % "commons-io" % "2.4"
)

val root = project.in(file(".")).enablePlugins(JavaAppPackaging)

scalacOptions ++= Seq("-feature", "-deprecation")

Keys.mainClass in Compile := Some("com.lvxingpai.yunkai.YunkaiServer")

parallelExecution in Test := false

fork in run := true

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