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

fork := true

libraryDependencies ++= Seq(
  "com.azavea.geotrellis" %% "geotrellis-contrib-vlm" % "3.13.0",
  "com.azavea.geotrellis" %% "geotrellis-contrib-gdal" % "3.13.0",
  "org.scalatest"  %% "scalatest" % "3.0.7" % Test
)
