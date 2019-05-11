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
