name := "eyeriss-chisel3"

version := "0.0.1"

organization := "gate"

scalaVersion := "2.12.4"

crossScalaVersions := Seq("2.12.6", "2.11.12")

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-language:reflectiveCalls")

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
// The following are the current "release" versions.
val defaultVersions = Map(
  "chisel3" -> "3.1.+",
  "chisel-iotesters" -> "1.2.+"
  )

libraryDependencies ++= (Seq("chisel3","chisel-iotesters").map {
  dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep)) })

libraryDependencies ++= Seq("org.json4s" %% "json4s-jackson" % "3.6.1")

libraryDependencies += "edu.berkeley.cs" %% "dsptools" % "1.1.6"

def scalacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // If we're building with Scala > 2.11, enable the compile option
    //  switch to support our anonymous Bundle definitions:
    //  https://github.com/scala/bug/issues/10047
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 => Seq()
      case _ => Seq("-Xsource:2.11")
    }
  }
}

def javacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // Scala 2.12 requires Java 8. We continue to generate
    //  Java 7 compatible code for Scala 2.11
    //  for compatibility with old clients.
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 =>
        Seq("-source", "1.7", "-target", "1.7")
      case _ =>
        Seq("-source", "1.8", "-target", "1.8")
    }
  }
}

libraryDependencies  ++= Seq(
  "org.scalanlp" %% "breeze" % "0.13.2",
  "org.scalanlp" %% "breeze-natives" % "0.13.2",
  "org.scalanlp" %% "breeze-viz" % "0.13.2"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots"),
  Resolver.sonatypeRepo("releases")
)

// Recommendations from http://www.scalatest.org/user_guide/using_scalatest_with_sbt
logBuffered in Test := false

// Disable parallel execution when running tests.
//  Running tests in parallel on Jenkins currently fails.
parallelExecution in Test := false

scalacOptions ++= scalacOptionsVersion(scalaVersion.value)

javacOptions ++= javacOptionsVersion(scalaVersion.value)

trapExit := false
