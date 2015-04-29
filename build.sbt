name := """yunkai"""

version := "0.1"

scalaVersion := "2.11.5"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "org.slf4j" % "slf4j-log4j12" % "1.7.12",
  "org.apache.thrift" % "libthrift" % "0.9.2"
)

fork in run := true
