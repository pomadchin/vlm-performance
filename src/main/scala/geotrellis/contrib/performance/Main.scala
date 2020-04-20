package geotrellis.contrib.performance

import geotrellis.layer.{SpaceTimeKey, TileLayerMetadata, ZoomedLayoutScheme}
import geotrellis.raster.MultibandTile
import geotrellis.spark.MultibandTileLayerRDD
import geotrellis.spark.pyramid.Pyramid
import geotrellis.spark.store.s3.{S3LayerReader, S3LayerWriter}
import geotrellis.spark.util.SparkUtils
import geotrellis.store.LayerId
import geotrellis.store.index.ZCurveKeyIndexMethod
import geotrellis.store.s3.{S3AttributeStore, S3ClientProducer}
import geotrellis.store.json.Implicits._
import org.apache.spark.SparkConf

import java.time.{LocalDateTime, ZoneOffset, ZonedDateTime}

object Main extends Serializable {
  case class DayOfYearTuple(year: Int, dayOfYear: Int) extends Serializable {

    def toZonedDateTime: Option[ZonedDateTime] = {
      try {
        Some(
          LocalDateTime
            .of(year, 1, 1, 0, 0)
            .atZone(ZoneOffset.UTC)
            .withDayOfYear(dayOfYear))
      } catch {
        case _: Throwable => None
      }
    }
  }

  def main(args: Array[String]): Unit = {
    implicit val sc = SparkUtils.createSparkContext("GeoTrellis SpaceTimeIngest", new SparkConf(true))

    try {
      val layerId = LayerId("spacetimekey-test", 14)

      val attributeStore = new S3AttributeStore("geotrellis-demo", "catalog/spacetimekey-test")

      val dts: List[ZonedDateTime] = (for {
        year <- Seq(2020)
        day  <- 1 to 366
      } yield DayOfYearTuple(year, day).toZonedDateTime).flatten.toList

      val reader = S3LayerReader("geotrellis-test-non-public", "spacetimekey-test", S3ClientProducer.get())
      val writer = S3LayerWriter(attributeStore)

      val index = ZCurveKeyIndexMethod.byDay()
      val tileLayerRdd = reader.read[SpaceTimeKey, MultibandTile, TileLayerMetadata[SpaceTimeKey]](layerId)
      val layoutScheme = ZoomedLayoutScheme(tileLayerRdd.metadata.crs)

      def sink: (MultibandTileLayerRDD[SpaceTimeKey], Int) => Unit = { (rdd, zoom) =>
        val id = layerId.copy(zoom = zoom)
        attributeStore.write(id, "times", dts)
        writer.write[SpaceTimeKey, MultibandTile, TileLayerMetadata[SpaceTimeKey]](id, rdd, index)
      }

      def buildPyramid(zoom: Int, rdd: MultibandTileLayerRDD[SpaceTimeKey]): List[(Int, MultibandTileLayerRDD[SpaceTimeKey])] = {
        if (zoom >= 1) {
          sink(rdd, zoom)
          val pyramidLevel@(nextZoom, nextRdd) = Pyramid.up(rdd, layoutScheme, zoom, None)
          pyramidLevel :: buildPyramid(nextZoom, nextRdd)
        } else {
          sink(rdd, zoom)
          List((zoom, rdd))
        }
      }

      // reingest the original layer
      sink(tileLayerRdd, layerId.zoom)
      // create temporal singnature
      attributeStore.write(layerId, "times", dts)
      // build up a pyramid
      buildPyramid(13, tileLayerRdd).foreach { case (_, rdd) => rdd.unpersist(true) }

    } finally sc.stop()
  }
}
