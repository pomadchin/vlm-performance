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

import org.apache.spark.{SparkConf, SparkContext}
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model._
import com.amazonaws.services.s3.AmazonS3URI

import scala.collection.JavaConverters._

package object performance extends Serializable {
  val nlcdPath    = "s3://geotrellis-test/nlcd-geotiff"
  val nlcdURI     = new AmazonS3URI(nlcdPath)
  val catalogPath = "s3://geotrellis-test/rastersource-performance/"
  val catalogURI  = new AmazonS3URI(catalogPath)

  val nedPath     = "s3://azavea-datahub/raw/ned-13arcsec-geotiff"
  val nedURI      = new AmazonS3URI(nedPath)

  @transient lazy val s3Client: S3Client = S3Client.create()

  def getPaths(bucket: String, prefix: String, keyConstructor: S3Object => String) = {
    val listRequest = ListObjectsV2Request.builder()
      .bucket(bucket)
      .prefix(prefix)
      .build()

    s3Client
      .listObjectsV2Paginator(listRequest)
      .contents
      .asScala
      .map { obj => s"s3://geotrellis-test/${obj.key}" }
      .toList
  }

  lazy val nlcdPaths: List[String] =
    getPaths(nlcdURI.getBucket, nlcdURI.getKey, { (obj: S3Object) => s"s3://geotrellis-test/${obj.key}" })

  lazy val nedPaths: List[String] =
    getPaths(nlcdURI.getBucket, nlcdURI.getKey, { (obj: S3Object) => s"s3://azavea-datahub/${obj.key}" })

  def createSparkContext(appName: String, sparkConf: SparkConf = createSparkConf): SparkContext = {
    sparkConf
      .setAppName(appName)
      .setIfMissing("spark.master", "local[*]")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .set("spark.kryo.registrator", classOf[geotrellis.spark.io.kryo.KryoRegistrator].getName)

    new SparkContext(sparkConf)
  }
}
