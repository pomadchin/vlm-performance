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
