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

package geotrellis.contrib

import geotrellis.contrib.performance.conf.GDALEnabled
import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.config.S3Config
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource
import geotrellis.spark.io.s3.AmazonS3Client
import geotrellis.spark.util.SparkUtils.createSparkConf

import org.apache.spark.{SparkConf, SparkContext}
import com.amazonaws.services.s3.{AmazonS3ClientBuilder, AmazonS3URI}

package object performance extends Serializable {
  val nlcdPath    = "s3://geotrellis-test/nlcd-geotiff"
  val nlcdURI     = new AmazonS3URI(nlcdPath)
  val catalogPath = "s3://geotrellis-test/rastersource-performance/"
  val catalogURI  = new AmazonS3URI(catalogPath)

  val nedPath     = "s3://azavea-datahub/raw/ned-13arcsec-geotiff"
  val nedURI      = new AmazonS3URI(nedPath)

  @transient lazy val s3Client: AmazonS3Client =
    if (S3Config.allowGlobalRead) {
      val builder = AmazonS3ClientBuilder
        .standard()
        .withForceGlobalBucketAccessEnabled(true)

      val client = S3Config.region.fold(builder) { region => builder.setRegion(region); builder }.build

      new AmazonS3Client(client)
    } else {
      val builder = AmazonS3ClientBuilder.standard()
      val client = S3Config.region.fold(builder) { region => builder.setRegion(region); builder }.build
      new AmazonS3Client(client)
    }

  lazy val nlcdPaths: List[String] =
    s3Client
      .listKeys(nlcdURI.getBucket, nlcdURI.getKey)
      .toList
      .map { key => s"s3://geotrellis-test/$key" }

  lazy val nedPaths: List[String] =
    s3Client
      .listKeys(nedURI.getBucket, nedURI.getKey)
      .toList
      .map { key => s"s3://azavea-datahub/$key" }

  def getRasterSource(uri: String, gdalEnabled: Boolean = GDALEnabled.enabled): RasterSource =
    if(gdalEnabled) GDALRasterSource(uri) else GeoTiffRasterSource(uri)

  def createSparkContext(appName: String, sparkConf: SparkConf = createSparkConf): SparkContext = {
    sparkConf
      .setAppName(appName)
      .setIfMissing("spark.master", "local[*]")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .set("spark.kryo.registrator", classOf[geotrellis.spark.io.kryo.KryoRegistrator].getName)

    new SparkContext(sparkConf)
  }
}
