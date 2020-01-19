import Versions._
import sbt.Keys.scalacOptions

resolvers ++= Seq(
  Resolver.mavenLocal,
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

def moduleSettings(moduleName: String): Seq[Def.SettingsDefinition] =
  Seq(
    organization := "FruTTecH",
    name := moduleName,
    version := "0.0.1",
    scalaVersion := "2.13.1",
    maxErrors := 3,
    commonSettings,
    zioDeps,
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val commonSettings = Seq(
// Refine scalac params from tpolecat for interactive console
  scalacOptions in console --= Seq(
    "-Xfatal-warnings"
  )
)

lazy val zioDeps = libraryDependencies ++= Seq(
  "dev.zio" %% "zio"          % ZioVersion,
  "dev.zio" %% "zio-streams"  % ZioVersion,
  "dev.zio" %% "zio-test"     % ZioVersion % "test",
  "dev.zio" %% "zio-test-sbt" % ZioVersion % "test"
)

lazy val core = (project in file("core")).settings(moduleSettings("zio-event-sourcing"): _*)

lazy val serializerProtobuf =
  (project in file("serializers/protobuf"))
    .settings(moduleSettings("zio-event-sourcing-serializer-protobuf"): _*)
    .settings(
      PB.protoSources in Test := Seq(file("serializers/protobuf/src/test/protobuf"))
    )
    .settings(
      PB.targets in Compile := Seq(
        scalapb.gen() -> (sourceManaged in Compile).value
      ),
      libraryDependencies += "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
    )
    .dependsOn(core)

lazy val fileStorage =
  (project in file("storage/file"))
    .settings(moduleSettings("zio-event-sourcing-file-store"): _*)
    .dependsOn(core)

lazy val root = project.aggregate(core, serializerProtobuf, fileStorage)
// Aliases
addCommandAlias("rel", "reload")
addCommandAlias("com", "all compile test:compile it:compile")
addCommandAlias("fix", "all compile:scalafix test:scalafix")
addCommandAlias("fmt", "all scalafmtSbt scalafmtAll")
