name := "vlm-performance"
version := "0.1.0-SNAPSHOT"
scalaVersion := "2.11.12"
crossScalaVersions := Seq("2.12.8", "2.11.12")
organization := "com.azavea"
scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-language:implicitConversions",
  "-language:reflectiveCalls",
  "-language:higherKinds",
  "-language:postfixOps",
  "-language:existentials",
  "-feature"
)

headerLicense := Some(HeaderLicense.ALv2("2019", "Azavea"))

resolvers ++= Seq(
  Resolver.bintrayRepo("azavea", "maven"),
  Resolver.bintrayRepo("azavea", "geotrellis"),
  "locationtech-releases" at "https://repo.locationtech.org/content/groups/releases",
  "locationtech-snapshots" at "https://repo.locationtech.org/content/groups/snapshots"
 )

outputStrategy := Some(StdoutOutput)

addCompilerPlugin("org.typelevel" % "kind-projector" % "0.10.0" cross CrossVersion.binary)
addCompilerPlugin("org.scalamacros" %% "paradise" % "2.1.1" cross CrossVersion.full)

fork := true

libraryDependencies ++= Seq(
  "com.azavea.geotrellis" %% "geotrellis-contrib-vlm"  % "3.13.0",
  "com.azavea.geotrellis" %% "geotrellis-contrib-gdal" % "3.13.0",
  "org.apache.spark"      %% "spark-core"              % "2.4.2",
  "org.apache.spark"      %% "spark-sql"               % "2.4.2",
  "org.scalatest"         %% "scalatest"               % "3.0.7" % Test
)

test in assembly := {}
assemblyShadeRules in assembly := {
  Seq(ShadeRule.rename("shapeless.**" -> s"com.azavea.shaded.shapeless.@1").inAll)
}

assemblyMergeStrategy in assembly := {
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case PathList("META-INF", xs@_*) => xs match {
    case ("MANIFEST.MF" :: Nil) => MergeStrategy.discard
    // Concatenate everything in the services directory to keep GeoTools happy.
    case ("services" :: _ :: Nil) => MergeStrategy.concat
    // Concatenate these to keep JAI happy.
    case ("javax.media.jai.registryFile.jai" :: Nil) | ("registryFile.jai" :: Nil) | ("registryFile.jaiext" :: Nil) => MergeStrategy.concat
    case (name :: Nil) => {
      // Must exclude META-INF/*.([RD]SA|SF) to avoid "Invalid signature file digest for Manifest main attributes" exception.
      if (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".SF")) MergeStrategy.discard else MergeStrategy.first
    }
    case _ => MergeStrategy.first
  }
  case _ => MergeStrategy.first
}

// Settings from sbt-lighter plugin that will automate creating and submitting this job to EMR
import sbtlighter._

LighterPlugin.disable

lazy val Ingest = config("ingest")
lazy val IngestRasterSource = config("ingest-raster-source")
lazy val IngestRasterSourceV1 = config("ingest-raster-source-v1")

lazy val EMRSettings = LighterPlugin.baseSettings ++ Seq(
  sparkEmrRelease := "emr-5.23.0",
  sparkAwsRegion := "us-east-1",
  sparkEmrApplications := Seq("Hadoop", "Spark", "Ganglia", "Zeppelin"),
  sparkEmrBootstrap := List(
    // using us buildings bootstrap
    BootstrapAction("Install GDAL + dependencies",
      "s3://geotrellis-test/usbuildings/bootstrap.sh",
      "s3://geotrellis-test/usbuildings",
      "v1.0")),
  sparkS3JarFolder := "s3://geotrellis-test/rastersource-performance/jars",
  sparkInstanceCount := 51,
  sparkMasterType := "m4.xlarge",
  sparkCoreType := "m4.xlarge",
  sparkMasterPrice := Some(0.1),
  sparkCorePrice := Some(0.1),
  sparkClusterName := "GeoTrellis VLM Performance",
  sparkEmrServiceRole := "EMR_DefaultRole",
  sparkInstanceRole := "EMR_EC2_DefaultRole",
  sparkJobFlowInstancesConfig := sparkJobFlowInstancesConfig.value.withEc2KeyName("geotrellis-emr"),
  sparkS3LogUri := Some("s3://geotrellis-test/rastersource-performance/logs"),
  sparkEmrConfigs := List(
    EmrConfig("spark").withProperties(
      "maximizeResourceAllocation" -> "true"
    ),
    EmrConfig("spark-defaults").withProperties(
      "spark.driver.maxResultSize" -> "3G",
      "spark.dynamicAllocation.enabled" -> "true",
      "spark.shuffle.service.enabled" -> "true",
      "spark.shuffle.compress" -> "true",
      "spark.shuffle.spill.compress" -> "true",
      "spark.rdd.compress" -> "true",
      "spark.driver.extraJavaOptions" -> "-Djava.library.path=/usr/local/lib",
      "spark.executor.extraJavaOptions" -> "-XX:+UseParallelGC -Dgeotrellis.s3.threads.rdd.write=64 -Djava.library.path=/usr/local/lib",
      "spark.executorEnv.LD_LIBRARY_PATH" -> "/usr/local/lib"
    ),
    EmrConfig("spark-env").withProperties(
      "LD_LIBRARY_PATH" -> "/usr/local/lib"
    ),
    EmrConfig("yarn-site").withProperties(
      "yarn.resourcemanager.am.max-attempts" -> "1",
      "yarn.nodemanager.vmem-check-enabled" -> "false",
      "yarn.nodemanager.pmem-check-enabled" -> "false"
    )
  )
)

inConfig(Ingest)(EMRSettings ++ Seq(
  (mainClass in Compile) := Some("geotrellis.contrib.performance.Ingest"),
  sparkSubmitConfs := Map(
    "spark.driver.memory" -> "4200M", 
    "spark.driver.cores" -> "2",
    "spark.executor.memory" -> "1500M",
    "spark.executor.cores" -> "1",
    "spark.yarn.driver.memoryOverhead" -> "700",
    "spark.yarn.executor.memoryOverhead" -> "700"
  )
))

inConfig(IngestRasterSource)(EMRSettings ++ Seq(
  (mainClass in Compile) := Some("geotrellis.contrib.performance.IngestRasterSource"),
  sparkSubmitConfs := Map(
    "spark.driver.memory" -> "4200M",
    "spark.driver.cores" -> "2",
    "spark.executor.memory" -> "4200M",
    "spark.executor.cores" -> "1",
    "spark.yarn.driver.memoryOverhead" -> "700",
    "spark.yarn.executor.memoryOverhead" -> "700"
  )
))

inConfig(IngestRasterSourceV1)(EMRSettings ++ Seq(
  (mainClass in Compile) := Some("geotrellis.contrib.performance.IngestRasterSourceV1"),
  sparkSubmitConfs := Map(
    "spark.driver.memory" -> "4200M",
    "spark.driver.cores" -> "2",
    "spark.executor.memory" -> "4200M",
    "spark.executor.cores" -> "1",
    "spark.yarn.driver.memoryOverhead" -> "700",
    "spark.yarn.executor.memoryOverhead" -> "700"
  )
))
