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

import geotrellis.proj4._
import geotrellis.raster.{DoubleCellType, MultibandTile}
import geotrellis.raster.resample.Bilinear
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.index.ZCurveKeyIndexMethod
import geotrellis.spark.pyramid.Pyramid
import geotrellis.spark.tiling._
import geotrellis.vector._

import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD

object Ingest {
  def main(args: Array[String]): Unit = {
    val (bucket, key, tpe) = args.toList match {
      case "ned" :: Nil => (nedURI.getBucket, nedURI.getKey, "ned")
      case _            => (nlcdURI.getBucket, nlcdURI.getKey, "nlcd")
    }

    val layerName = s"$tpe-v2-rastersource-avro"

    implicit val sc: SparkContext = createSparkContext("Ingest", new SparkConf(true))
    val targetCRS = WebMercator
    val method = Bilinear
    val layoutScheme = ZoomedLayoutScheme(targetCRS, tileSize = 256)

    val inputRdd: RDD[(ProjectedExtent, MultibandTile)] =
      S3GeoTiffRDD.spatialMultiband(bucket, key)

    val (_, rasterMetaData) = TileLayerMetadata.fromRDD(inputRdd, FloatingLayoutScheme(512))

    val tiled: RDD[(SpatialKey, MultibandTile)] = inputRdd.tileToLayout(rasterMetaData.cellType, rasterMetaData.layout, Bilinear)

    val (zoom, reprojected): (Int, RDD[(SpatialKey, MultibandTile)] with Metadata[TileLayerMetadata[SpatialKey]]) =
      MultibandTileLayerRDD(tiled, rasterMetaData)
        .reproject(targetCRS, layoutScheme, method)

    val attributeStore = S3AttributeStore(catalogURI.getBucket, catalogURI.getKey)
    val writer = S3LayerWriter(attributeStore)

    Pyramid.upLevels(reprojected, layoutScheme, zoom, method) { (rdd, z) =>
      val layerId = LayerId(layerName, z)
      if(attributeStore.layerExists(layerId)) S3LayerDeleter(attributeStore).delete(layerId)
      writer.write(layerId, rdd.withContext(_.mapValues(_.convert(DoubleCellType))), ZCurveKeyIndexMethod)
    }
  }
}
