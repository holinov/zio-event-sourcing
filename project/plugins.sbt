addSbtPlugin("org.scalameta"             % "sbt-scalafmt" % "2.4.0")
addSbtPlugin("ch.epfl.scala"             % "sbt-scalafix" % "0.9.18-1")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.13")

// ScalaPB
addSbtPlugin("com.thesamet"                    % "sbt-protoc"     % "0.99.34")
libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.10.7"

// Release and `Bintray publish` plugins
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.6")
