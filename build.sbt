import scala.collection.Seq

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.5"

lazy val root = (project in file("."))
  .aggregate(messenger)
  .settings(
    name := "web-chat2",
    idePackagePrefix := Some("org.chats"),
    assembly / mainClass := Some("org.chats.Main")
  )

val pekkoVersion = "1.1.3"
val pekkoHttpVersion = "1.1.0"
val commonDependencies = Seq(
  "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion,
  "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
  "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
  "org.apache.pekko" %% "pekko-http-spray-json" % pekkoHttpVersion,
  "ch.qos.logback" % "logback-classic" % "1.5.15"
)

libraryDependencies ++= commonDependencies

lazy val messenger = project
  .in(file("messenger"))
  .settings(
    name := "messenger",
    idePackagePrefix := Some("org.chats"),
    libraryDependencies ++= commonDependencies,
  )

// Customize the merge strategy to discard duplicated files and merge Pekko conf files
ThisBuild / assemblyMergeStrategy := {
  // Akka/Pekko wants us to merge reference.conf and version.conf files:
  // https://doc.akka.io/libraries/akka-core/current/additional/packaging.html#maven-jarjar-onejar-or-assembly
  case PathList("reference.conf") => MergeStrategy.concat
  case PathList("version.conf") => MergeStrategy.concat
  // Discard module-info.class because they are treated as duplicates by assembly plugin and Scala doesn't care for them.
  // This might cause issues on newer JVM versions (17+) that start caring about them. Maybe revisit later.
  case PathList("module-info.class") => MergeStrategy.discard
  // Use default strategy for the rest of files
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}