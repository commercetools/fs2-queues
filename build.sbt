import sbtcrossproject.CrossProject
import com.typesafe.tools.mima.core._

import laika.config.PrettyURLs
import laika.config.LinkConfig
import laika.config.ApiLinks
import laika.config.SourceLinks

ThisBuild / tlBaseVersion := "0.4"

ThisBuild / organization := "com.commercetools"
ThisBuild / organizationName := "Commercetools GmbH"
ThisBuild / startYear := Some(2024)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / tlCiDependencyGraphJob := false

val Scala213 = "2.13.14"
ThisBuild / crossScalaVersions := Seq(Scala213, "3.3.3")
ThisBuild / scalaVersion := Scala213

ThisBuild / tlSonatypeUseLegacyHost := true

lazy val root =
  tlCrossRootProject.aggregate(
    core,
    testing,
    azureServiceBus,
    awsSQS,
    awsSqsIt,
    gcpPubSub,
    gcpPubSubIt,
    circe,
    otel4s,
    unidocs)

ThisBuild / tlSitePublishBranch := Some("main")

val commonSettings = List(
  libraryDependencies ++= Seq(
    "org.scalameta" %%% "munit" % Versions.munit % Test,
    "org.typelevel" %%% "munit-cats-effect-3" % Versions.munitCatsEffect % Test,
    "org.typelevel" %%% "cats-effect-testkit" % "3.5.3" % Test
  ),
  scalacOptions += (scalaVersion.value match {
    case Scala213 => "-Wunused"
    case _ => "-Wunused:all"
  })
)

lazy val core: CrossProject = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "fs2-queues-core",
    // TODO: Remove once next version is published
    mimaBinaryIssueFilters ++= List(
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.commercetools.queue.QueuePublisher.sink")
    ),
    libraryDependencies ++= List(
      "co.fs2" %%% "fs2-core" % Versions.fs2
    )
  )

lazy val testing = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("testing"))
  .settings(commonSettings)
  .settings(
    name := "fs2-queues-testing",
    libraryDependencies ++= List(
      "org.typelevel" %%% "cats-collections-core" % "0.9.8"
    ),
    tlVersionIntroduced := Map("3" -> "0.4.0", "2.13" -> "0.4.0")
  )
  .dependsOn(core)

lazy val testkit = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("testkit"))
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .settings(
    name := "fs2-queues-testkit",
    libraryDependencies ++= List(
      "org.scalameta" %%% "munit" % Versions.munit,
      "org.typelevel" %%% "munit-cats-effect-3" % Versions.munitCatsEffect,
      "org.slf4j" % "slf4j-simple" % "2.0.16"
    )
  )
  .dependsOn(core)

// for sqs integration test, start a localstack with sqs
ThisBuild / githubWorkflowBuildPreamble := List(
  WorkflowStep.Use(
    UseRef.Public(owner = "LocalStack", repo = "setup-localstack", ref = "main"),
    name = Some("Install localstack"),
    params = Map("image-tag" -> "latest"),
    env = Map("SERVICES" -> "sqs")
  ),
  WorkflowStep.Use(
    UseRef.Public(owner = "google-github-actions", repo = "setup-gcloud", ref = "v2"),
    name = Some("Install gcloud"),
    params = Map("install_components" -> "beta,pubsub-emulator")
  ),
  WorkflowStep.Run(commands = List("./gcp/pubsub/emulator/start.sh &"), name = Some("Run PubSub emulator"))
)

lazy val otel4s = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("otel4s"))
  .settings(commonSettings)
  .settings(
    name := "fs2-queues-otel4s",
    description := "Support for metrics and tracing using otel4s",
    libraryDependencies ++= List(
      "org.typelevel" %%% "otel4s-core" % "0.9.0"
    )
  )
  .dependsOn(core, testing % Test)

lazy val circe = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("circe"))
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
  .settings(commonSettings)
  .settings(
    name := "fs2-queues-azure-service-bus",
    libraryDependencies ++= List(
      "com.azure" % "azure-messaging-servicebus" % "7.17.0"
    )
  )
  .dependsOn(core, testkit % Test)

lazy val azureServiceBusIt = project
  .in(file("azure/service-bus/integration"))
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= List(
      "com.azure" % "azure-identity" % "1.11.1"
    )
  )
  .dependsOn(azureServiceBus.jvm % Test, testkit.jvm % Test)

lazy val awsSQS = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("aws/sqs"))
  .settings(commonSettings)
  .settings(
    name := "fs2-queues-aws-sqs",
    libraryDependencies ++= List(
      "software.amazon.awssdk" % "sqs" % "2.25.50"
    )
  )
  .dependsOn(core)

lazy val awsSqsIt = project
  .in(file("aws/sqs/integration"))
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .dependsOn(awsSQS.jvm % Test, testkit.jvm % Test)

lazy val gcpPubSub = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("gcp/pubsub"))
  .settings(commonSettings)
  .settings(
    name := "fs2-queues-gcp-pubsub",
    // TODO: Remove once next version is published
    mimaBinaryIssueFilters ++= List(
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "com.commercetools.queue.gcp.pubsub.PubSubAdministration.this"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.commercetools.queue.gcp.pubsub.PubSubClient.unmanaged"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.commercetools.queue.gcp.pubsub.PubSubClient.this"),
      ProblemFilters.exclude[DirectMissingMethodProblem](
        "com.commercetools.queue.gcp.pubsub.PubSubClient.unmanaged$default$5"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.commercetools.queue.gcp.pubsub.PubSubPublisher.this"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.commercetools.queue.gcp.pubsub.PubSubPuller.this"),
      ProblemFilters.exclude[DirectMissingMethodProblem]("com.commercetools.queue.gcp.pubsub.PubSubSubscriber.this")
    ),
    libraryDependencies ++= List(
      "com.google.cloud" % "google-cloud-pubsub" % "1.129.3",
      "com.google.cloud" % "google-cloud-monitoring" % "3.47.0"
    )
  )
  .dependsOn(core)

lazy val gcpPubSubIt = project
  .in(file("gcp/pubsub/integration"))
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .dependsOn(gcpPubSub.jvm % Test, testkit.jvm % Test)

lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .settings(
    tlSiteApiPackage := Some("com.commercetools.queue"),
    tlSiteHelium := CTTheme(tlSiteHelium.value),
    laikaConfig := tlSiteApiUrl.value
      .fold(laikaConfig.value) { apiUrl =>
        laikaConfig.value.withConfigValue(
          LinkConfig.empty
            .addApiLinks(ApiLinks(baseUri = apiUrl.toString().dropRight("index.html".size)))
            .addSourceLinks(
              SourceLinks(baseUri = "https://github.com/commercetools/fs2-queues", suffix = "scala")
            ))
      },
    laikaExtensions += PrettyURLs,
    tlFatalWarnings := false,
    libraryDependencies ++= List(
      "com.azure" % "azure-identity" % "1.11.1",
      "org.typelevel" %% "cats-effect-testkit" % "3.5.4"
    )
  )
  .dependsOn(circe.jvm, azureServiceBus.jvm, awsSQS.jvm, gcpPubSub.jvm, otel4s.jvm, testing.jvm)

lazy val unidocs = project
  .in(file("unidocs"))
  .enablePlugins(TypelevelUnidocPlugin)
  .settings(
    name := "fs2-queues-docs",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(
      core.jvm,
      circe.jvm,
      azureServiceBus.jvm,
      awsSQS.jvm,
      gcpPubSub.jvm,
      otel4s.jvm,
      testing.jvm)
  )
