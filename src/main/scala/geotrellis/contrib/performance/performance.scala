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

import geotrellis.spark.util.SparkUtils.createSparkConf
import geotrellis.store.s3.{AmazonS3URI, S3ClientProducer}

import org.apache.spark.{SparkConf, SparkContext}
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import scala.collection.JavaConverters._

package object performance extends Serializable {
  val nlcdPath    = "s3://geotrellis-test/nlcd-geotiff"
  val nlcdURI     = new AmazonS3URI(nlcdPath)
  val catalogPath = "s3://geotrellis-test/rastersource-performance/"
  val catalogURI  = new AmazonS3URI(catalogPath)

  val nedPath     = "s3://azavea-datahub/raw/ned-13arcsec-geotiff"
  val nedURI      = new AmazonS3URI(nedPath)

  @transient val s3Client = S3ClientProducer.get()

  lazy val nlcdPaths: List[String] = {
    val listRequest = ListObjectsV2Request.builder()
      .bucket(nlcdURI.getBucket)
      .prefix(nlcdURI.getKey)
      .build()

    s3Client.listObjectsV2(listRequest)
      .contents
      .asScala
      .toList
      .map { key => s"s3://geotrellis-test/$key" }
  }

  lazy val nedPaths: List[String] = {
    val listRequest = ListObjectsV2Request.builder()
      .bucket(nedURI.getBucket)
      .prefix(nedURI.getKey)
      .build()

    s3Client.listObjectsV2(listRequest)
      .contents
      .asScala
      .toList
      .map { key => s"s3://azavea-datahub/$key" }
  }

  def createSparkContext(appName: String, sparkConf: SparkConf = createSparkConf): SparkContext = {
    sparkConf
      .setAppName(appName)
      .setIfMissing("spark.master", "local[*]")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .set("spark.kryo.registrator", classOf[geotrellis.spark.store.kryo.KryoRegistrator].getName)

    new SparkContext(sparkConf)
  }
}
