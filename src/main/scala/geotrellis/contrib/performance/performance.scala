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

import com.amazonaws.services.s3.{AmazonS3ClientBuilder, AmazonS3URI}
import geotrellis.contrib.performance.conf.GDALEnabled
import geotrellis.contrib.vlm.RasterSource
import geotrellis.contrib.vlm.config.S3Config
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.contrib.vlm.geotiff.GeoTiffRasterSource
import geotrellis.spark.io.s3.AmazonS3Client

package object performance extends Serializable {
  val nlcdPath    = "s3://geotrellis-test/nlcd-geotiff"
  val nlcdURI     = new AmazonS3URI(nlcdPath)
  val catalogPath = "s3://geotrellis-test/rastersource-performance/"

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

  lazy val nlcdPaths: List[String] = s3Client.listKeys(nlcdURI.getBucket, nlcdURI.getKey).toList

  def getRasterSource(uri: String): RasterSource =
    if(GDALEnabled.enabled) GDALRasterSource(uri) else GeoTiffRasterSource(uri)
}
