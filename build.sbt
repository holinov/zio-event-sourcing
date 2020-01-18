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
    //name := "zio-event-sourcing",
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

lazy val fileStorage =
  (project in file("storage/file")).settings(moduleSettings("zio-event-sourcing-file-store"): _*).dependsOn(core)

lazy val root = project.aggregate(core, fileStorage)
// Aliases
addCommandAlias("rel", "reload")
addCommandAlias("com", "all compile test:compile it:compile")
addCommandAlias("fix", "all compile:scalafix test:scalafix")
addCommandAlias("fmt", "all scalafmtSbt scalafmtAll")
