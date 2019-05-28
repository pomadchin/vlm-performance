/*
 * Copyright 2019 Azavea
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geotrellis.contrib.performance

import geotrellis.contrib.performance.conf.{GDALEnabled, IngestVersion}
import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.spark.{RasterSourceRDD, RasterSummary, SpatialPartitioner}
import geotrellis.proj4._
import geotrellis.raster.{DoubleCellType, MultibandTile}
import geotrellis.raster.resample.Bilinear
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.index.ZCurveKeyIndexMethod
import geotrellis.spark.pyramid.Pyramid
import geotrellis.spark.tiling.{LayoutLevel, ZoomedLayoutScheme}

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import cats.syntax.option._

object IngestRasterSource {
  import geotrellis.contrib.vlm.avro._

  def main(args: Array[String]): Unit = {
    val (paths, tpe, gdalEnabled) = args.toList match {
      case "ned" :: "gdal" :: Nil  => (nedPaths, "ned", true)
      case "nlcd" :: "gdal" :: Nil => (nlcdPaths, "nlcd", true)
      case "ned" :: _ :: Nil       => (nedPaths, "ned", false)
      case "nlcd" :: _ :: Nil      => (nlcdPaths, "nlcd", false)
      case "ned" :: Nil            => (nedPaths, "ned", GDALEnabled.enabled)
      case _                       => (nlcdPaths, "nlcd", GDALEnabled.enabled)
    }

    val layerName = s"$tpe-${IngestVersion.version}-rastersource-${if(gdalEnabled) "gdal" else "geotiff"}"

    implicit val sc: SparkContext = createSparkContext("IngestRasterSource", new SparkConf(true))
    val targetCRS = WebMercator
    val method = Bilinear
    val layoutScheme = ZoomedLayoutScheme(targetCRS, tileSize = 256)

    val sourceRDD: RDD[RasterSource] =
      sc.parallelize(paths, paths.length)
        .map(uri => getRasterSource(uri, gdalEnabled).reproject(targetCRS, method).convert(DoubleCellType): RasterSource)
        .cache()

    val summary = RasterSummary.fromRDD[RasterSource, Long](sourceRDD)
    val LayoutLevel(zoom, layout) = summary.levelFor(layoutScheme)
    val contextRDD = RasterSourceRDD.tiledLayerRDD(sourceRDD, layout, rasterSummary = summary.some)

    // println(s"contextRDD.count(): ${contextRDD.count()}")

    val attributeStore = S3AttributeStore(catalogURI.getBucket, catalogURI.getKey)
    val writer = S3LayerWriter(attributeStore)

    writer.write(LayerId(layerName, zoom), contextRDD, ZCurveKeyIndexMethod)

    /*Pyramid.upLevels(contextRDD, layoutScheme, zoom, method) { (rdd, z) =>
      val layerId = LayerId(layerName, z)
      if(attributeStore.layerExists(layerId)) S3LayerDeleter(attributeStore).delete(layerId)
      writer.write(layerId, rdd, ZCurveKeyIndexMethod)
    }*/
  }
}
