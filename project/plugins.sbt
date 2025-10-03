ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

val pluginSbtScoverageVersion = sys.props.getOrElse(
  "plugin.sbtscoverage.version", "2.3.0"
)

addSbtPlugin("org.scoverage" % "sbt-scoverage" % pluginSbtScoverageVersion)
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.4")
addSbtPlugin("org.scalastyle" % "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.2.10")
addSbtPlugin("net.nmoncho" % "sbt-dependency-check" % "1.7.1")
addDependencyTreePlugin
