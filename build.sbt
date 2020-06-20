import com.typesafe.sbt.pgp.PgpKeys.publishSigned
import org.scalajs.sbtplugin.cross.CrossProject
import ReleaseTransformations._

import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys
import MimaKeys.{mimaPreviousArtifacts, mimaBinaryIssueFilters}
import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.core.ProblemFilters._

lazy val buildSettings = Seq(
  organization := "org.typelevel",
  scalaVersion := "2.13.2",
  crossScalaVersions := Seq("2.10.7", "2.11.12", "2.12.11", "2.13.2"),
  sourceGenerators in Compile += Def.task(Boilerplate.genCode((sourceManaged in Compile).value)).taskValue
)

lazy val commonSettings = Seq(
  incOptions := incOptions.value.withLogRecompileOnMacro(false),

  scalacOptions := Seq(
    "-feature",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked"
  ),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("public"),
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    "bintray/non" at "http://dl.bintray.com/non/maven"
  ),
  libraryDependencies ++= Seq(
    "com.github.dmytromitin" %% "macro-compat"  % "1.1.2-SNAPSHOT",
    "com.chuusai"            %% "shapeless"     % (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, scalaMajor)) if scalaMajor >= 11 => "2.4.0-M1"
      case _                                         => "2.3.3"
    }) % Test,
    "org.typelevel"          %% "simulacrum"    % "1.0.0"    % Test,
    "org.scalatest"          %% "scalatest"     % "3.1.2"    % Test,
    "org.scalacheck"         %% "scalacheck"    % "1.14.3"   % Test,
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)
  ),
  scmInfo :=
    Some(ScmInfo(
      url("https://github.com/milessabin/export-hook"),
      "scm:git:git@github.com:milessabin/export-hook.git"
    ))
) ++ crossVersionSharedSources ++ scalaMacroDependencies

//lazy val commonJsSettings = Seq(
//  scalaJSStage in Global := FastOptStage,
//  parallelExecution in Test := false
//)

lazy val commonJvmSettings = Seq(
  parallelExecution in Test := false
)

lazy val coreSettings = buildSettings ++ commonSettings ++ publishSettings

lazy val root = project.in(file("."))
  .aggregate(/*coreJS,*/ coreJVM)
  .dependsOn(/*coreJS,*/ coreJVM)
  .settings(coreSettings:_*)
  .settings(noPublishSettings)

lazy val core = crossProject.crossType(CrossType.Pure)
  .settings(moduleName := "export-hook")
  .settings(coreSettings:_*)
  .settings(mimaSettings:_*)
//  .jsSettings(commonJsSettings:_*)
  .jvmSettings(commonJvmSettings:_*)

lazy val coreJVM = core.jvm
//lazy val coreJS = core.js

addCommandAlias("validate", ";root;compile;mimaReportBinaryIssues;test")
addCommandAlias("release-all", ";root;release")
//addCommandAlias("js", ";project coreJS")
addCommandAlias("jvm", ";project coreJVM")
addCommandAlias("root", ";project root")

lazy val scalaMacroDependencies: Seq[Setting[_]] = Seq(
  libraryDependencies ++= Seq(
    scalaOrganization.value % "scala-compiler" % scalaVersion.value % Provided,
    scalaOrganization.value % "scala-reflect"  % scalaVersion.value % Provided
  ),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      // if scala 2.13+ is used, quasiquotes and macro-annotations are merged into scala-reflect
      case Some((2, scalaMajor)) if scalaMajor >= 13 => Seq()
      // if scala 2.11+ is used, quasiquotes are merged into scala-reflect
      case Some((2, scalaMajor)) if scalaMajor >= 11 => Seq(
        compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.patch)
      )
      // in Scala 2.10, quasiquotes are provided by macro paradise
      case Some((2, 10)) => Seq(
        compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.patch),
        "org.scalamacros" %% "quasiquotes" % "2.1.1" cross CrossVersion.binary
      )
    }
  },
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, scalaMajor)) if scalaMajor >= 13 => Seq("-Ymacro-annotations")
      case _                                         => Seq()
    }
  }
)

lazy val crossVersionSharedSources: Seq[Setting[_]] =
  Seq(Compile, Test).map { sc =>
    (unmanagedSourceDirectories in sc) ++= {
      (unmanagedSourceDirectories in sc ).value.map {
        dir:File => new File(dir.getPath + "_" + scalaBinaryVersion.value)
      }
    }
  }

lazy val publishSettings = Seq(
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  homepage := Some(url("https://github.com/milessabin/export-hook")),
  licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra := (
    <developers>
      <developer>
        <id>milessabin</id>
        <name>Miles Sabin</name>
        <url>http://milessabin.com/blog</url>
      </developer>
    </developers>
  )
)

lazy val mimaSettings = mimaDefaultSettings ++ Seq(
  mimaPreviousArtifacts := { Set() },

  mimaBinaryIssueFilters ++= {
    // Filtering the methods that were added since the checked version
    // (these only break forward compatibility, not the backward one)
    Seq(
    )
  }
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val sharedReleaseProcess = Seq(
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
    pushChanges
  )
)

credentials ++= (for {
  username <- Option(System.getenv().get("SONATYPE_USERNAME"))
  password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq
