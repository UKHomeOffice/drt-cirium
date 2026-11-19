import sbt.*

object AppDependencies {
  private val pekkoVersion = "1.7.1"
  private val pekkoHttpVersion = "1.4.2"
  private val logbackContribVersion = "0.1.5"
  private val scalatestVersion = "3.2.20"

  val compileDependencies: Seq[ModuleID] = Seq(
    "com.github.gphat"          %% "censorinus"            % "2.1.16",
    "org.apache.pekko"          %% "pekko-http"            % pekkoHttpVersion,
    "org.apache.pekko"          %% "pekko-http-spray-json" % pekkoHttpVersion,
    "org.apache.pekko"          %% "pekko-stream"          % pekkoVersion,
    "org.apache.pekko"          %% "pekko-slf4j"           % pekkoVersion,
    "org.apache.pekko"          %% "pekko-pki"             % pekkoVersion,
    "ch.qos.logback"             % "logback-classic"       % "1.6.7",
    "joda-time"                  % "joda-time"             % "2.15.1",
    "ch.qos.logback.contrib"     % "logback-json-classic"  % logbackContribVersion,
    "ch.qos.logback.contrib"     % "logback-jackson"       % logbackContribVersion,
    "com.fasterxml.jackson.core" % "jackson-databind"      % "2.21.8" // for compatibility with deps in Drtv2
  )

  val testDependencies: Seq[ModuleID] = Seq(
    "org.apache.pekko" %% "pekko-http-testkit"   % pekkoHttpVersion % Test,
    "org.apache.pekko" %% "pekko-testkit"        % pekkoVersion     % Test,
    "org.apache.pekko" %% "pekko-stream-testkit" % pekkoVersion     % Test,
    "org.specs2"       %% "specs2-core"          % "4.23.0"         % Test,
    "org.scalactic"    %% "scalactic"            % scalatestVersion % Test,
    "org.scalatest"    %% "scalatest"            % scalatestVersion % Test
  )

  val allDependencies: Seq[ModuleID] = compileDependencies ++ testDependencies
}
