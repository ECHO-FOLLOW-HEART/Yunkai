credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

addSbtPlugin("com.twitter" % "scrooge-sbt-plugin" % "3.14.1")

//addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0-M1")

// AspectJ
addSbtPlugin("com.typesafe.sbt" % "sbt-aspectj" % "0.10.2")
