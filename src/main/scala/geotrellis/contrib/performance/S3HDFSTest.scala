package geotrellis.contrib.performance

import geotrellis.raster.gdal.GDALRasterSource
import geotrellis.raster.geotiff.GeoTiffRasterSource

object S3HDFSTest {
  def timedCreate[T](startMsg: String, endMsg: String)(f: => T): T = {
    println(startMsg)
    val s = System.currentTimeMillis
    val result = f
    val e = System.currentTimeMillis
    val t = "%,d".format(e - s)
    println(s"\t$endMsg (in $t ms)")
    result
  }

  def main(args: Array[String]): Unit = {
    val s3uri: String = args(0)
    val hdfsuri: String = args(2)
    println("-----GeoTiff-S3-------")
    (1 to 1000).foreach { _ =>
      timedCreate("start geotiff s3", "end geotiff s3")(GeoTiffRasterSource(s3uri).read())
    }
    println("---------------------")
    println("-----GDAL-S3-------")
    (1 to 1000).foreach { _ =>
      timedCreate("start gdal s3", "end gdal s3")(GDALRasterSource(s3uri).read())
    }
    println("---------------------")
    println("-----GeoTiff-HDFS-------")
    (1 to 1000).foreach { _ =>
      timedCreate("start geotiff hdfs", "end geotiff hdfs")(GeoTiffRasterSource(hdfsuri).read())
    }
    println("---------------------")
    println("-----GDAL-HDFS-------")
    (1 to 1000).foreach { _ =>
      timedCreate("start gdal hdfs", "end gdal hdfs")(GDALRasterSource(hdfsuri).read() )
    }
    println("---------------------")
  }
}
