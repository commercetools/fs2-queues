import laika.config.PrettyURLs

ThisBuild / tlBaseVersion := "0.0"

ThisBuild / organization := "com.commercetools"
ThisBuild / organizationName := "Commercetools GmbH"
ThisBuild / startYear := Some(2024)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / tlCiDependencyGraphJob := false
ThisBuild / developers := List(
  tlGitHubDev("satabin", "Lucas Satabin")
)

val Scala213 = "2.13.12"
ThisBuild / crossScalaVersions := Seq(Scala213, "3.3.3")
ThisBuild / scalaVersion := Scala213

lazy val root = tlCrossRootProject.aggregate(core, azureServiceBus, awsSQS, circe, otel4s)

ThisBuild / tlSitePublishBranch := Some("main")

val commonSettings = List(
  libraryDependencies ++= Seq(
    "co.fs2" %%% "fs2-core" % Versions.fs2,
    "org.scalameta" %%% "munit" % Versions.munit % Test,
    "org.typelevel" %%% "munit-cats-effect-3" % Versions.munitCatsEffect % Test,
    "org.typelevel" %%% "cats-collections-core" % "0.9.8" % Test,
    "org.typelevel" %%% "cats-effect-testkit" % "3.5.3" % Test
  ),
  scalacOptions += (scalaVersion.value match {
    case Scala213 => "-Wunused"
    case _ => "-Wunused:all"
  })
)

lazy val core = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .settings(
    name := "fs2-queues-core"
  )

lazy val otel4s = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("otel4s"))
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .settings(
    name := "fs2-queues-otel4s",
    description := "Support for metrics and tracing using otel4s",
    libraryDependencies ++= List(
      "org.typelevel" %%% "otel4s-core" % "0.4.0"
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val circe = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("circe"))
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .settings(
    name := "fs2-queues-circe",
    libraryDependencies ++= List(
      "io.circe" %%% "circe-parser" % Versions.circe
    )
  )
  .dependsOn(core)

lazy val azureServiceBus = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("azure/service-bus"))
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .settings(
    name := "fs2-queues-azure-service-bus",
    libraryDependencies ++= List(
      "com.azure" % "azure-messaging-servicebus" % "7.15.1"
    )
  )
  .dependsOn(core)

lazy val awsSQS = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("aws/sqs"))
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .settings(
    name := "fs2-queues-aws-sqs",
    libraryDependencies ++= List(
      "software.amazon.awssdk" % "sqs" % "2.18.35"
    )
  )
  .dependsOn(core)

lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .settings(
    tlSiteApiPackage := Some("com.commercetools.queue"),
    tlSiteHelium := CTTheme(tlSiteHelium.value),
    laikaExtensions += PrettyURLs,
    tlFatalWarnings := false,
    libraryDependencies ++= List(
      "com.azure" % "azure-identity" % "1.11.1"
    )
  )
  .dependsOn(circe.jvm, azureServiceBus.jvm, awsSQS.jvm, otel4s.jvm)

lazy val unidocs = project
  .in(file("unidocs"))
  .enablePlugins(TypelevelUnidocPlugin)
  .settings(
    name := "fs2-queues-docs",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(core.jvm, circe.jvm, azureServiceBus.jvm, awsSQS.jvm)
  )
