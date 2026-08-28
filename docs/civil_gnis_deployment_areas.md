# Civil GNIS Deployment Areas GeoPackage

`civil_gnis_deployment_areas.gpkg` is a reduced, spatially indexed extract of the
USGS Geographic Names Information System (GNIS) Domestic Names dataset. It is
intended for lightweight offline validation of TIM `packetID` deployment-area
identifiers.

The generated file is copied into the library at
`timvalidator/src/main/resources/us/dot/its/jpo/timvalidator/gnis/`. Maven includes
that resource in the published `timvalidator` JAR so applications consuming the
library do not need to deploy the GeoPackage separately.

## Source and filtering

The source was the USGS national pipe-delimited file
`DomesticNames_National.txt`, available from the [GNIS download
page](https://www.usgs.gov/us-board-on-geographic-names/download-gnis-data).
GNIS source coordinates use NAD83 (EPSG:4269).

The extract retained only records where:

- `feature_class` is exactly `Civil`;
- the primary coordinate is not the GNIS unknown-location value `0.0, 0.0`; and
- the following columns are retained, in this order:

```text
feature_id|feature_name|feature_class|state_name|prim_lat_dec|prim_long_dec
```

The reduced text file can be reproduced on Ubuntu/WSL with:

```bash
awk -F'|' 'BEGIN {
    OFS="|"
    print "feature_id|feature_name|feature_class|state_name|prim_lat_dec|prim_long_dec"
}
NR > 1 && $3 == "Civil" && $16 != "" && $17 != "" && !($16 == "0.0" && $17 == "0.0") {
    print $1, $2, $3, $4, $16, $17
}' DomesticNames_National.txt > Civil_GNIS_DeploymentAreas.txt
```

## GeoPackage creation

The reduced text was imported as point geometry using longitude for X and
latitude for Y. Coordinates were assigned their source CRS, NAD83 (EPSG:4269),
and transformed to WGS 84 (EPSG:4326) to match J2735 TIM anchor coordinates.

An equivalent GDAL command is:

```bash
ogr2ogr \
  -f GPKG \
  civil_gnis_deployment_areas.gpkg \
  "CSV:Civil_GNIS_DeploymentAreas.txt" \
  -oo SEPARATOR=PIPE \
  -oo AUTODETECT_TYPE=YES \
  -oo X_POSSIBLE_NAMES=prim_long_dec \
  -oo Y_POSSIBLE_NAMES=prim_lat_dec \
  -s_srs EPSG:4269 \
  -t_srs EPSG:4326 \
  -nln gnis_deployment_areas \
  -nlt POINT \
  -lco GEOMETRY_NAME=geom \
  -lco SPATIAL_INDEX=YES
```

The original latitude and longitude columns are retained in addition to the
generated `geom` point. The GeoPackage RTree spatial index supports bounding-box
queries against `geom`.

## Feature-ID index

A separate unique index supports direct lookup of the GNIS identifier decoded
from the first three bytes of a TIM `packetID`:

```bash
ogrinfo civil_gnis_deployment_areas.gpkg \
  -dialect SQLite \
  -sql "CREATE UNIQUE INDEX IF NOT EXISTS idx_gnis_feature_id
        ON gnis_deployment_areas(feature_id)"
```

## Result and verification

The generated artifact has:

- layer name `gnis_deployment_areas`;
- 64,911 point features;
- WGS 84 geometry registered as EPSG:4326;
- geometry column `geom` and feature-ID column `fid`;
- no non-`Civil` records;
- no `0.0, 0.0` primary coordinates;
- an RTree spatial index; and
- a unique index named `idx_gnis_feature_id`.

Inspect the layer and verify both indexes with:

```bash
ogrinfo -so civil_gnis_deployment_areas.gpkg gnis_deployment_areas

ogrinfo civil_gnis_deployment_areas.gpkg \
  -dialect SQLite \
  -sql "SELECT HasSpatialIndex('gnis_deployment_areas', 'geom') AS has_spatial_index"

ogrinfo civil_gnis_deployment_areas.gpkg \
  -dialect SQLite \
  -sql "SELECT name, sql FROM sqlite_master
        WHERE name = 'idx_gnis_feature_id'"
```

`HasSpatialIndex` should return `1`, and the final query should return the unique
`feature_id` index definition.
