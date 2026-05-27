import sbt.*

object AppDependencies {
  private val pekkoVersion = "1.4.0"
  private val pekkoHttpVersion = "1.3.0"
  private val logbackContribVersion = "0.1.5"
  private val scalatestVersion = "3.2.19"

  val compileDependencies: Seq[ModuleID] = Seq(
    "com.github.gphat"          %% "censorinus"            % "2.1.16",
    "org.apache.pekko"          %% "pekko-http"            % pekkoHttpVersion,
    "org.apache.pekko"          %% "pekko-http-spray-json" % pekkoHttpVersion,
    "org.apache.pekko"          %% "pekko-stream"          % pekkoVersion,
    "org.apache.pekko"          %% "pekko-slf4j"           % pekkoVersion,
    "org.apache.pekko"          %% "pekko-pki"             % pekkoVersion,
    "ch.qos.logback"             % "logback-classic"       % "1.4.14",
    "joda-time"                  % "joda-time"             % "2.12.7",
    "ch.qos.logback.contrib"     % "logback-json-classic"  % logbackContribVersion,
    "ch.qos.logback.contrib"     % "logback-jackson"       % logbackContribVersion,
    "com.fasterxml.jackson.core" % "jackson-databind"      % "2.16.1",
    "org.codehaus.janino"        % "janino"                % "3.1.11"
  )

  val testDependencies: Seq[ModuleID] = Seq(
    "org.apache.pekko" %% "pekko-http-testkit"   % pekkoHttpVersion % Test,
    "org.apache.pekko" %% "pekko-testkit"        % pekkoVersion     % Test,
    "org.apache.pekko" %% "pekko-stream-testkit" % pekkoVersion     % Test,
    "org.specs2"       %% "specs2-core"          % "4.20.9"         % Test,
    "org.scalactic"    %% "scalactic"            % scalatestVersion % Test,
    "org.scalatest"    %% "scalatest"            % scalatestVersion % Test
  )

  val allDependencies: Seq[ModuleID] = compileDependencies ++ testDependencies
}
