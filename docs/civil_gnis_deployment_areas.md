# Civil GNIS Deployment Areas CSV

`civil_gnis_deployment_areas.csv` is a reduced extract of the USGS Geographic
Names Information System (GNIS) Domestic Names dataset. It supports lightweight,
offline validation of TIM `packetID` deployment-area identifiers.

The CSV is stored at
`timvalidator/src/main/resources/us/dot/its/jpo/timvalidator/gnis/`. Maven includes
it in the published `timvalidator` JAR, so consuming applications do not need to
deploy a separate data file or database.

## Source and filtering

The source is the USGS national pipe-delimited `DomesticNames_National.txt` file,
available from the [GNIS download
page](https://www.usgs.gov/us-board-on-geographic-names/download-gnis-data).

The extract contains only records where:

- `feature_class` is exactly `Civil`;
- the primary coordinate is present and is not the GNIS unknown-location value
  `0.0, 0.0`; and
- the following columns are retained, in this order:

```text
feature_id,feature_name,feature_class,state_name,prim_lat_dec,prim_long_dec
```

GDAL can produce the CSV directly from the national download while applying the
filter and correct CSV quoting:

```bash
ogr2ogr \
  -f CSV \
  civil_gnis_deployment_areas.csv \
  "CSV:DomesticNames_National.txt" \
  -oo SEPARATOR=PIPE \
  -oo AUTODETECT_TYPE=YES \
  -where "feature_class = 'Civil' AND prim_lat_dec IS NOT NULL
          AND prim_long_dec IS NOT NULL
          AND NOT (prim_lat_dec = 0 AND prim_long_dec = 0)" \
  -select feature_id,feature_name,feature_class,state_name,prim_lat_dec,prim_long_dec \
  -lco SEPARATOR=COMMA \
  -lco STRING_QUOTING=IF_AMBIGUOUS
```

GNIS primary coordinates are NAD83 decimal degrees. For this lightweight sanity
check they are treated as longitude/latitude coordinates compatible with J2735
WGS 84 anchors; the practical datum difference is immaterial at the validation
distances used by the project.

## Runtime indexing

The library reads the packaged CSV lazily on the first GNIS lookup and shares the
loaded dataset across validator instances. It creates:

- an in-memory map keyed by `feature_id` for direct packet-prefix lookups; and
- a JTS `STRtree` of representative feature points for geographic bounds queries.

No SQLite, GeoPackage, GDAL, or external service is required at runtime.

## Result and verification

The generated artifact has 64,911 records, all with unique feature identifiers,
`Civil` feature classes, and nonzero primary coordinates. Basic shell checks are:

```bash
head -n 1 civil_gnis_deployment_areas.csv
wc -l civil_gnis_deployment_areas.csv
```

The expected line count is 64,912 including the header.
