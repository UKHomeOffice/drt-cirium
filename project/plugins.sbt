ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

addSbtPlugin("org.scalameta"          % "sbt-scalafmt"         % "2.6.1")
addSbtPlugin("org.scoverage"          % "sbt-scoverage"        % "2.4.4")
addSbtPlugin("org.wartremover"        % "sbt-wartremover"      % "3.5.7")
addSbtPlugin("com.timushev.sbt"       % "sbt-updates"          % "0.6.4")
addSbtPlugin("com.github.sbt"         % "sbt-native-packager"  % "1.11.7")
addSbtPlugin("org.johnnei.scapegoat" %% "sbt-scapegoat"        % "1.3.7")
addSbtPlugin("net.nmoncho"            % "sbt-dependency-check" % "1.9.0")

addDependencyTreePlugin
