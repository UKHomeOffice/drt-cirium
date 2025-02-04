lazy val scala = "2.13.15"
lazy val akkaVersion = "2.9.5" // last version with license key requirement
lazy val akkaHttpVersion = "10.6.3" // last version dependent on akka 2.9.5
lazy val specs2Version = "4.20.9"
lazy val jodaTimeVersion = "2.12.7"
lazy val logBackClassicVersion = "1.5.16"
lazy val logbackContribVersion = "0.1.5"
lazy val jacksonDatabindVersion = "2.16.1"
lazy val censorinusVersion = "2.1.16"
lazy val scalatestVersion = "3.2.19"
lazy val janinoVersion = "3.1.11"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "uk.gov.homeoffice",
    )),
    scalaVersion := scala,
    version := sys.env.getOrElse("DRONE_BUILD_NUMBER", sys.env.getOrElse("BUILD_ID", "DEV")),
    name := "drt-cirium",

    resolvers ++= Seq(
      "Akka library repository".at("https://repo.akka.io/maven"),
      "Artifactory Realm libs release local" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/libs-release-local/",
    ),

    dockerBaseImage := "openjdk:11-jre-slim-buster",

    libraryDependencies ++= Seq(
      "com.github.gphat" %% "censorinus" % censorinusVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.akka" %% "akka-pki" % akkaVersion,
      "ch.qos.logback" % "logback-classic" % logBackClassicVersion,
      "joda-time" % "joda-time" % jodaTimeVersion,

      "ch.qos.logback.contrib" % "logback-json-classic" % logbackContribVersion,
      "ch.qos.logback.contrib" % "logback-jackson" % logbackContribVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion,
      "org.codehaus.janino" % "janino" % janinoVersion,

      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
      "org.specs2" %% "specs2-core" % specs2Version % Test,
      "org.scalactic" %% "scalactic" % scalatestVersion,
      "org.scalatest" %% "scalatest" % scalatestVersion % "test",
    )
  )
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)

Test / fork := true

publishTo := {
  val artifactory = "https://artifactory.digital.homeoffice.gov.uk/"

  if (isSnapshot.value)
    Some("snapshot" at artifactory + "artifactory/libs-snapshot-local")
  else
    Some("release" at artifactory + "artifactory/libs-release-local")
}

// Enable publishing the jar produced by `test:package`
Test / packageBin / publishArtifact := true

// Enable publishing the test API jar
Test / packageDoc / publishArtifact := true

// Enable publishing the test sources jar
Test / packageSrc / publishArtifact := true
