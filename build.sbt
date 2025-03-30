import scala.collection.Seq

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.5"

lazy val root = (project in file("."))
  .aggregate(messenger)
  .aggregate(chatsApi)
  .aggregate(cliClient)
  .settings(
    name := "web-chat2",
    idePackagePrefix := Some("org.chats"),
  )

val pekkoVersion = "1.1.3"
val pekkoHttpVersion = "1.1.0"
val commonDependencies = Seq(
  "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion,
  "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
  "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
  "org.apache.pekko" %% "pekko-http-spray-json" % pekkoHttpVersion,
  // include an slf4j implementation so that we have logging
  "ch.qos.logback" % "logback-classic" % "1.5.15",
  "org.apache.pekko" %% "pekko-stream-typed" % pekkoVersion
)

lazy val messenger = project
  .in(file("messenger-api"))
  .settings(
    name := "messenger-api",
    idePackagePrefix := Some("org.chats"),
    libraryDependencies ++= commonDependencies ++ Seq(
      "org.apache.pekko" %% "pekko-cluster-typed" % pekkoVersion,
      "org.apache.pekko" %% "pekko-cluster-sharding-typed" % pekkoVersion,
      "org.apache.pekko" %% "pekko-persistence-typed" % pekkoVersion,
      "org.apache.pekko" %% "pekko-serialization-jackson" % pekkoVersion
    ),
    javacOptions += "-parameters"
  )

lazy val chatsApi = project
  .in(file("chats-api"))
  .settings(
    name := "chats-api",
    idePackagePrefix := Some("org.chats"),
    libraryDependencies ++= commonDependencies,
  )

lazy val cliClient = project
    .in(file("cli-client"))
    .settings(
      name := "cli-client",
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