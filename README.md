# VLM Performance project

This project is created to track down RasterSources API regressions.

NOTE: at this point, this project depends on [GeoTrellis Contrib 3.14.0-SNAPSHOT](https://github.com/geotrellis/geotrellis-contrib/tree/cc6b022d5f4ac1266b23962181d00cc9cce79e40),
it requires GeoTrellis Contrib local publish.

## Notes

Working on a cluster take into account the fact that GDAL requires a different strategy with the resources allocation.
It is not possible to use `maximizeResourceAllocation` flag with using JNI bindings. 

As the result of this work, was also figured out that `maximizeResourceAllocation` in general is not the best solution 
for GeoTrellis ingests.

### GDAL Tips

Pay attention to GDAL proper configuration:

```conf
gdal.options {
  GDAL_DISABLE_READDIR_ON_OPEN     = "TRUE" # we don't usually want to read the entire dir with tiff metadata
  CPL_VSIL_CURL_ALLOWED_EXTENSIONS = ".tif" # filter files read by extension to speed up reads
  GDAL_MAX_DATASET_POOL_SIZE       = "256" # number of allocated GDAL datasets
  GDAL_CACHEMAX                    = "1000" # number in megabyes to limit GDAL apetite
  # CPL_DEBUG                        = "ON" # to eanble GDAL logging on all nodes
}
```

For `50` `i3.xlarge` nodes it turned out that `GDAL_CACHEMAX = 1000` and `200` single core executors 
looks like a good option. For `25` `i3.xlarge` nodes `GDAL_CACHEMAX = 500` and `70` single core executors, etc.

### Ingest Results

The test dataset: [s3://azavea-datahub/raw/ned-13arcsec-geotiff](s3://azavea-datahub/raw/ned-13arcsec-geotiff)
The test dataset size: `1115 Objects - 210.7 GB`

#### 20 i3.xlarge nodes

Legacy GeoTrellis Ingest: `max resources allocation`, `1 core per executor`, `1500M` RAM per executor
![Ingest](img/20/ingest-i3-200exec.png)

GeoTiff RasterSources Ingest: `max resources allocation`, `1 core per executor`, `1500M` RAM per executor
![GeoTiffRaterSource](img/20/ingest-rs-gdal-i3-70.png)

GDAL RasterSources Ingest: `70 executors`, `1 core per executor`, `1500M` RAM per executor, `GDAL_CACHEMAX = 500`
![GDALRasterSource](img/20/ingest-rs-gdal-i3-70.png)

#### 50 i3.xlarge nodes

Legacy GeoTrellis Ingest: `200 executors`, `1 core per executor`, `4200M` RAM per executor.
With less RAM job is failing, maxmizing resources usage kills job as well.
![Ingest](img/50/ingest-i3-50-4200M.png)

GeoTiff RasterSources Ingest: `200 executors`, `1 core per executor`, `4200M` RAM per executor
With less RAM job is failing, maxmizing resources usage kills job as well.
![GeoTiffRasterSource](img/50/geotiff-i3-50-4200M.png)

GDAL RasterSources Ingest: `200 executors`, `1 core per executor`, `1500M` RAM per executor, `GDAL_CACHEMAX = 1000`
![GDALRasterSource](img/50/gdal-i3-50-1000size.png)


### Conclusion

The new API completely replaces the old one. The two ingests are a bit different. GDAL Ingest requires a bit
more complicated settings tuning, however, the new API is not slower and sometimes even faster.
