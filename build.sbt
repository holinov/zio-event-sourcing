import Versions._
import sbt.Keys._

resolvers ++= Seq(
  Resolver.mavenLocal,
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.jcenterRepo
)

lazy val commonSettings = Seq(
  // Refine scalac params from tpolecat for interactive console
  scalacOptions in console --= Seq(
    "-Xfatal-warnings"
  ),
  addCompilerPlugin(scalafixSemanticdb),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
)

def moduleSettings(moduleName: String): Seq[Def.SettingsDefinition] = Seq(
  organization := "FruTTecH",
  name := moduleName,
  version := "0.0.4",
  scalaVersion := "2.13.1",
  maxErrors := 3,
  commonSettings,
  zioDeps,
  testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
)

lazy val zioDeps = libraryDependencies ++= Seq(
  "dev.zio" %% "zio"          % ZioVersion,
  "dev.zio" %% "zio-streams"  % ZioVersion,
  "dev.zio" %% "zio-test"     % ZioVersion % "test",
  "dev.zio" %% "zio-test-sbt" % ZioVersion % "test"
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
        "dev.zio"     %% "zio-rocksdb" % ZioRocksDb,
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
    )
    .dependsOn(core, serializerProtobuf)

lazy val root = project
  .settings(skip in publish := true)
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
