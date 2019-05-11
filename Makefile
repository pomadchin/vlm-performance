VLM_TEST_VERSION ?= 0.1.0
VLM_TEST_VERSION_SUFFIX ?= -SNAPSHOT
ASSEMBLY := ./target/scala-2.11/vlm-performance-assembly-${VLM_TEST_VERSION}${VLM_TEST_VERSION_SUFFIX}.jar
S3_URI := s3://geotrellis-test/rastersource-performance/jars/vlm-performance-assembly-${VLM_TEST_VERSION}${VLM_TEST_VERSION_SUFFIX}.jar
KEY_PAIR_FILE := ${HOME}/.ssh/geotrellis-emr.pem

${ASSEMBLY}: $(call rwildcard, ./src, *.scala) ./build.sbt
	./sbt assembly -no-colors
	@touch -m ${ASSEMBLY}

ifndef CLUSTER_ID
CLUSTER_ID=$(shell cat .cluster_id)
endif

proxy:
	aws emr socks --cluster-id ${CLUSTER_ID} --key-pair-file ${KEY_PAIR_FILE}

ssh:
	aws emr ssh --cluster-id ${CLUSTER_ID} --key-pair-file ${KEY_PAIR_FILE}

upload-assembly: ${ASSEMBLY}
	cd terraform; aws emr put --cluster-id ${CLUSTER_ID} --key-pair-file ${KEY_PAIR_FILE} \
	--src ${ASSEMBLY} --dest /tmp/vlm-performance-assembly-${GEOTRELLIS_VERSION}${GEOTRELLIS_VERSION_SUFFIX}.jar

upload-assembly-s3: ${ASSEMBLY}
	aws s3 cp ${ASSEMBLY} ${S3_URI}

ingest-ned:
	aws emr add-steps --output text --cluster-id ${CLUSTER_ID} \
--steps Type=CUSTOM_JAR,Name="Ingest",Jar=command-runner.jar,Args=[\
spark-submit,--master,yarn-cluster,\
--class,geotrellis.contrib.performance.Ingest,\
--driver-memory,4200M,\
--driver-cores,2,\
--executor-memory,4200M,\
--executor-cores,1,\
--num-executors,200,\
--conf,spark.yarn.executor.memoryOverhead=700,\
--conf,spark.yarn.driver.memoryOverhead=700,\
${S3_URI},ned\
] | cut -f2 | tee last-step-id.txt

ingest-rastersource-ned-gdal:
	aws emr add-steps --output text --cluster-id ${CLUSTER_ID} \
--steps Type=CUSTOM_JAR,Name="Ingest RasterSource",Jar=command-runner.jar,Args=[\
spark-submit,--master,yarn-cluster,\
--class,geotrellis.contrib.performance.IngestRasterSource,\
--driver-memory,4200M,\
--driver-cores,2,\
--executor-memory,1500M,\
--executor-cores,1,\
--num-executors,200,\
--conf,spark.yarn.executor.memoryOverhead=700,\
--conf,spark.yarn.driver.memoryOverhead=700,\
${S3_URI},ned,gdal\
] | cut -f2 | tee last-step-id.txt

ingest-rastersource-ned-geotiff:
	aws emr add-steps --output text --cluster-id ${CLUSTER_ID} \
--steps Type=CUSTOM_JAR,Name="Ingest RasterSource",Jar=command-runner.jar,Args=[\
spark-submit,--master,yarn-cluster,\
--class,geotrellis.contrib.performance.IngestRasterSource,\
--driver-memory,4200M,\
--driver-cores,2,\
--executor-memory,4200M,\
--executor-cores,1,\
--num-executors,200,\
--conf,spark.yarn.executor.memoryOverhead=700,\
--conf,spark.yarn.driver.memoryOverhead=700,\
${S3_URI},ned,geotiff\
] | cut -f2 | tee last-step-id.txt

ingest-nlcd:
	aws emr add-steps --output text --cluster-id ${CLUSTER_ID} \
--steps Type=CUSTOM_JAR,Name="Ingest",Jar=command-runner.jar,Args=[\
spark-submit,--master,yarn-cluster,\
--class,geotrellis.contrib.performance.Ingest,\
--driver-memory,4200M,\
--driver-cores,2,\
--executor-memory,1500M,\
--executor-cores,1,\
--num-executors,20,\
--conf,spark.yarn.executor.memoryOverhead=700,\
--conf,spark.yarn.driver.memoryOverhead=700,\
${S3_URI},nlcd\
] | cut -f2 | tee last-step-id.txt

ingest-rastersource-nlcd-gdal:
	aws emr add-steps --output text --cluster-id ${CLUSTER_ID} \
--steps Type=CUSTOM_JAR,Name="Ingest RasterSource",Jar=command-runner.jar,Args=[\
spark-submit,--master,yarn-cluster,\
--class,geotrellis.contrib.performance.IngestRasterSource,\
--driver-memory,4200M,\
--driver-cores,2,\
--executor-memory,1500M,\
--executor-cores,1,\
--num-executors,20,\
--conf,spark.yarn.executor.memoryOverhead=700,\
--conf,spark.yarn.driver.memoryOverhead=700,\
${S3_URI},nlcd,gdal\
] | cut -f2 | tee last-step-id.txt

ingest-rastersource-nlcd-geotiff:
	aws emr add-steps --output text --cluster-id ${CLUSTER_ID} \
--steps Type=CUSTOM_JAR,Name="Ingest RasterSource",Jar=command-runner.jar,Args=[\
spark-submit,--master,yarn-cluster,\
--class,geotrellis.contrib.performance.IngestRasterSource,\
--driver-memory,4200M,\
--driver-cores,2,\
--executor-memory,1500M,\
--executor-cores,1,\
--num-executors,20,\
--conf,spark.yarn.executor.memoryOverhead=700,\
--conf,spark.yarn.driver.memoryOverhead=700,\
${S3_URI},nlcd,geotiff\
] | cut -f2 | tee last-step-id.txt
