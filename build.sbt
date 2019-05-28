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
  "com.azavea.geotrellis" %% "geotrellis-contrib-vlm"  % "3.14.0-SNAPSHOT",
  "com.azavea.geotrellis" %% "geotrellis-contrib-gdal" % "3.14.0-SNAPSHOT",
  "org.apache.spark"      %% "spark-core"              % "2.4.2",
  "org.apache.spark"      %% "spark-sql"               % "2.4.2",
  "org.scalatest"         %% "scalatest"               % "3.0.7" % Test
)

dependencyOverrides += "com.azavea.gdal" % "gdal-warp-bindings" % "33.5523882"

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
lazy val IngestRasterSourceGDAL = config("ingestRasterSourceGDAL")

lazy val EMRSettings = LighterPlugin.baseSettings ++ Seq(
  sparkEmrRelease := "emr-5.23.0",
  sparkAwsRegion := "us-east-1",
  sparkEmrApplications := Seq("Hadoop", "Spark", "Ganglia", "Zeppelin"),
  sparkEmrBootstrap := List(
    BootstrapAction(
      "Install GDAL dependencies",
      "s3://geotrellis-test/usbuildings/bootstrap.sh",
      "s3://geotrellis-test/usbuildings", "v1.0"
    )
  ),
  sparkS3JarFolder := "s3://geotrellis-test/rastersource-performance/jars",
  sparkInstanceCount := 21,
  sparkMasterType := "i3.xlarge",
  sparkCoreType := "i3.xlarge",
  sparkMasterPrice := Some(0.2),
  sparkCorePrice := Some(0.2),
  sparkClusterName := s"GeoTrellis VLM Performance ${sys.env.getOrElse("USER", "<anonymous user>")}",
  sparkEmrServiceRole := "EMR_DefaultRole",
  sparkInstanceRole := "EMR_EC2_DefaultRole",
  sparkMasterEbsSize := None, // Some(64)
  sparkCoreEbsSize := None, // Some(64)
  sparkJobFlowInstancesConfig := sparkJobFlowInstancesConfig.value.withEc2KeyName("geotrellis-emr"),
  sparkS3LogUri := Some("s3://geotrellis-test/rastersource-performance/logs"),
  sparkEmrConfigs := List(
    EmrConfig("spark").withProperties(
      "maximizeResourceAllocation" -> "false" // be careful with setting this param to true
    ),
    EmrConfig("spark-defaults").withProperties(
      "spark.driver.maxResultSize" -> "4200M",
      "spark.dynamicAllocation.enabled" -> "true",
      "spark.shuffle.service.enabled" -> "true",
      "spark.shuffle.compress" -> "true",
      "spark.shuffle.spill.compress" -> "true",
      "spark.rdd.compress" -> "true",
      "spark.driver.extraJavaOptions" -> "-XX:+UseParallelGC -XX:+UseParallelOldGC -XX:OnOutOfMemoryError='kill -9 %p' -Dgeotrellis.s3.threads.rdd.write=64",
      "spark.executor.extraJavaOptions" -> "-XX:+UseParallelGC -XX:+UseParallelOldGC -XX:OnOutOfMemoryError='kill -9 %p' -Dgeotrellis.s3.threads.rdd.write=64"
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

addCommandAlias("create-cluster", "ingest:sparkCreateCluster")
addCommandAlias("ingest-ned", "ingest:sparkSubmitMain geotrellis.contrib.performance.Ingest ned")
addCommandAlias("ingest-nlcd", "ingest:sparkSubmitMain geotrellis.contrib.performance.Ingest nlcd")
inConfig(Ingest)(EMRSettings ++ Seq(
  sparkSubmitConfs := Map(
    "spark.master" -> "yarn",
    "spark.driver.memory" -> "4200M",
    "spark.driver.cores" -> "2",
    "spark.executor.memory" -> "1500M",
    "spark.executor.cores" -> "1",
    /*"spark.dynamicAllocation.enabled" -> "false",
    "spark.executor.instances" -> "200",*/
    "spark.yarn.executor.memoryOverhead" -> "700",
    "spark.yarn.driver.memoryOverhead" -> "700"/*,
    "spark.dynamicAllocation.minExecutors" -> "200",
    "spark.dynamicAllocation.maxExecutors" -> "200"*/
  )
))

addCommandAlias("ingest-raster-source-ned-geotiff", "ingest:sparkSubmitMain geotrellis.contrib.performance.IngestRasterSource ned geotiff")
addCommandAlias("ingest-raster-source-nlcd-geotiff", "ingest:sparkSubmitMain geotrellis.contrib.performance.IngestRasterSource nlcd geotiff")
addCommandAlias("ingest-raster-source-ned-gdal", "ingestRasterSourceGDAL:sparkSubmitMain geotrellis.contrib.performance.IngestRasterSource ned gdal")
addCommandAlias("ingest-raster-source-nlcd-gdal", "ingestRasterSourceGDAL:sparkSubmitMain geotrellis.contrib.performance.IngestRasterSource nlcd gdal")
inConfig(IngestRasterSourceGDAL)(EMRSettings ++ Seq(
  sparkSubmitConfs := Map(
    "spark.master" -> "yarn",
    "spark.driver.memory" -> "4200M",
    "spark.driver.cores" -> "2",
    "spark.executor.memory" -> "4500M",
    "spark.executor.cores" -> "1",
    "spark.dynamicAllocation.enabled" -> "false",
    "spark.executor.instances" -> "250", // 70 for 20 nodes cluster
    "spark.yarn.executor.memoryOverhead" -> "700",
    "spark.yarn.driver.memoryOverhead" -> "700"/*,
    "spark.dynamicAllocation.minExecutors" -> "200",
    "spark.dynamicAllocation.maxExecutors" -> "200"*/
  )
))
