import sbtcrossproject.{crossProject, CrossType}
import sbtrelease.ReleaseStateTransformations._

skip in publish := true

def gitHash: String = sys.process.Process("git rev-parse HEAD").lineStream.head

val tagName = Def.setting {
  s"v${if (releaseUseGlobalVersion.value) (version in ThisBuild).value else version.value}"
}
val tagOrHash = Def.setting {
  if (isSnapshot.value) gitHash else tagName.value
}

val unusedWarnings = Def.setting(
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 11)) =>
      Seq("-Ywarn-unused-import")
    case _ =>
      Seq("-Ywarn-unused:imports")
  }
)

val Scala211 = "2.11.12"

lazy val buildSettings = Seq(
  name := "scodec-msgpack",
  scalaVersion := Scala211,
  crossScalaVersions := Seq(Scala211, "2.12.8", "2.13.0"),
  scalaJSStage in Global := FastOptStage,
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-Xlint",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions"
  ),
  scalacOptions ++= unusedWarnings.value,
  fullResolvers ~= { _.filterNot(_.name == "jcenter") },
  libraryDependencies ++= Seq(
    "org.scodec" %%% "scodec-core" % "1.11.4",
    "org.scalatest" %%% "scalatest" % "3.0.8" % "test",
    "org.scalacheck" %%% "scalacheck" % "1.14.0" % "test"
  ),
  buildInfoKeys := BuildInfoKey.ofN(
    organization,
    name,
    version,
    scalaVersion,
    sbtVersion,
    scalacOptions,
    licenses
  ),
  buildInfoPackage := "scodec.msgpack",
  buildInfoObject := "BuildInfoScodecMsgpack",
  releaseCrossBuild := true,
  publishTo in ThisBuild := sonatypePublishTo.value,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  publishArtifact in Test := false,
  releaseTagName := tagName.value,
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
    pushChanges
  ),
  organization := "com.github.xuwei-k",
  credentials ++= PartialFunction
    .condOpt(sys.env.get("SONATYPE_USER") -> sys.env.get("SONATYPE_PASS")) {
      case (Some(user), Some(pass)) =>
        Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
    }
    .toList,
  homepage := Some(url("https://github.com/xuwei-k/scodec-msgpack")),
  licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),
  pomExtra :=
    <developers>
      <developer>
        <id>xuwei-k</id>
        <name>Kenji Yoshida</name>
        <url>https://github.com/xuwei-k</url>
      </developer>
    </developers>
    <scm>
      <url>git@github.com:xuwei-k/scodec-msgpack.git</url>
      <connection>scm:git:git@github.com:xuwei-k/scodec-msgpack.git</connection>
      <tag>{tagOrHash.value}</tag>
    </scm>,
  description := "yet another msgpack implementation",
  pomPostProcess := { node =>
    import scala.xml._
    import scala.xml.transform._
    def stripIf(f: Node => Boolean) = new RewriteRule {
      override def transform(n: Node) =
        if (f(n)) NodeSeq.Empty else n
    }
    val stripTestScope = stripIf { n =>
      n.label == "dependency" && (n \ "scope").text == "test"
    }
    new RuleTransformer(stripTestScope).transform(node)(0)
  }
) ++ Seq(Compile, Test).flatMap(c => scalacOptions in (c, console) --= unusedWarnings.value)

lazy val msgpack = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("."))
  .settings(
    buildSettings: _*
  )
  .enablePlugins(
    BuildInfoPlugin
  )
  .jsSettings(
    scalacOptions += {
      val a = (baseDirectory in LocalRootProject).value.toURI.toString
      val g = "https://raw.githubusercontent.com/xuwei-k/scodec-msgpack/" + tagOrHash.value
      s"-P:scalajs:mapSourceURI:$a->$g/"
    }
  )
  .jvmSettings(
    libraryDependencies += "org.msgpack" % "msgpack-core" % "0.8.17" % "test"
  )

lazy val msgpackJVM = msgpack.jvm
lazy val msgpackJS = msgpack.js
