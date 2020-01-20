addSbtPlugin("org.scalameta"             % "sbt-scalafmt" % "2.3.0")
addSbtPlugin("ch.epfl.scala"             % "sbt-scalafix" % "0.9.11")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.10")

// ScalaPB
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.27")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.9.4"

// Bintray publish plugin
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.6")
