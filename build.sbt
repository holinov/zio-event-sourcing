import Versions._
import sbt.Keys._

resolvers ++= Seq(
  Resolver.mavenLocal,
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.jcenterRepo
)

def moduleSettings(moduleName: String): Seq[Def.SettingsDefinition] = Seq(
  organization := "FruTTecH",
  name := moduleName,
  maxErrors := 3,
  scalacOptions += "-Ywarn-unused",
  scalacOptions in console --= Seq(
    "-Xfatal-warnings"
  ),
  zioDeps,
  addCompilerPlugin(scalafixSemanticdb),
  releaseCrossBuild := true,
  testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
)

lazy val zioDeps = libraryDependencies ++= Seq(
  "dev.zio"                %% "zio"                     % ZioVersion,
  "dev.zio"                %% "zio-streams"             % ZioVersion,
  "dev.zio"                %% "zio-test"                % ZioVersion % "test",
  "dev.zio"                %% "zio-test-sbt"            % ZioVersion % "test",
  "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.4"
)

lazy val protobufSettings = Seq(
  PB.protoSources in Test ++= Seq(file("src/test/protobuf")),
  PB.targets in Compile := Seq(
    scalapb.gen() -> (sourceManaged in Compile).value
  ),
  libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
)

lazy val core = (project in file("core")).settings(moduleSettings("zio-event-sourcing"): _*)

lazy val serializerProtobuf =
  (project in file("serializers/protobuf"))
    .settings(moduleSettings("zio-event-sourcing-serializer-protobuf"): _*)
    .settings(protobufSettings: _*)
    .dependsOn(core)

lazy val fileStorage =
  (project in file("storage/file"))
    .settings(moduleSettings("zio-event-sourcing-file-store"): _*)
    .settings(protobufSettings: _*)
    .dependsOn(core, serializerProtobuf)

lazy val rockDbStorage =
  (project in file("storage/rocks-db"))
    .settings(moduleSettings("zio-event-sourcing-rocks-db-store"): _*)
    .settings(protobufSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio"     %% "zio-rocksdb" % ZioRocksDb exclude ("org.slf4j", "slf4j-api"),
        "org.rocksdb" % "rocksdbjni"   % "5.5.1"
      )
    )
    .dependsOn(core, serializerProtobuf)

lazy val cassandraStorage =
  (project in file("storage/cassandra"))
    .settings(moduleSettings("zio-event-sourcing-cassandra-store"): _*)
    .settings(protobufSettings: _*)
    .settings(
      libraryDependencies += "com.datastax.cassandra" % "cassandra-driver-core" % "3.8.0"
        exclude ("org.scala-lang.modules", "scala-collection-compat")
    )
    .dependsOn(core, serializerProtobuf)

skip in publish := true
scalaVersion := "2.13.1"
crossScalaVersions := Nil
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
crossScalaVersions := Seq("2.13.1", "2.12.10")

lazy val root = project
  .settings(skip in publish := true, crossScalaVersions := Nil, name := "zio-event-sourcing-all")
  .aggregate(
    core,
    serializerProtobuf,
    fileStorage,
    rockDbStorage,
    cassandraStorage
  )

// Aliases
addCommandAlias("rel", "reload")
addCommandAlias("com", "all compile test:compile it:compile")
addCommandAlias("fix", "all compile:scalafix test:scalafix")
addCommandAlias("fmt", "all scalafmtSbt scalafmtAll")
addCommandAlias("crel", "core/release cross")
