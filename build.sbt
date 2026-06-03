import net.nmoncho.sbt.dependencycheck.settings.{ AnalyzerSettings, NvdApiSettings }

lazy val root = (project in file("."))
  .settings(
    inThisBuild(
      List(
        organization := "uk.gov.homeoffice",
        scalaVersion := "2.13.18",
        version := "v" + sys.env.getOrElse("DRONE_BUILD_NUMBER", sys.env.getOrElse("BUILD_ID", "DEV"))
      )
    ),
    name := "drt-cirium",
    resolvers ++= Seq(
      "Akka library repository".at("https://repo.akka.io/maven"),
      "Artifactory Realm libs release local" at
        "https://artifactory.digital.homeoffice.gov.uk/artifactory/libs-release-local/"
    ),
    dockerBaseImage := "openjdk:11-jre-slim-buster",
    libraryDependencies ++= AppDependencies.allDependencies,
    addCommandAlias("scalafmtAll", "all scalafmtSbt scalafmt Test/scalafmt")
  )
  .settings(CodeCoverageSettings.codeCoverageSettings)
  .settings(SbtUpdatesSettings.sbtUpdatesSettings)
  .settings(WartRemoverSettings.wartRemoverSettings)
  .enablePlugins(DockerPlugin)
  .enablePlugins(JavaAppPackaging)

val nvdAPIKey = sys.env.getOrElse("NVD_API_KEY", "")

dependencyCheckNvdApi := NvdApiSettings(apiKey = nvdAPIKey)

publishTo := {
  val artifactory = "https://artifactory.digital.homeoffice.gov.uk/"

  if (isSnapshot.value)
    Some("snapshot" at artifactory + "artifactory/libs-snapshot-local")
  else
    Some("release" at artifactory + "artifactory/libs-release-local")
}

ThisBuild / dependencyCheckAnalyzers := dependencyCheckAnalyzers.value.copy(
  ossIndex = AnalyzerSettings.OssIndex(
    enabled = Some(false),
    url = None,
    batchSize = None,
    requestDelay = None,
    useCache = None,
    warnOnlyOnRemoteErrors = None,
    username = None,
    password = None
  )
)

// Enable publishing the jar produced by `test:package`
Test / packageBin / publishArtifact := true

// Enable publishing the test API jar
Test / packageDoc / publishArtifact := true

// Enable publishing the test sources jar
Test / packageSrc / publishArtifact := true
