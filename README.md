# TIM Validation

This repository contains a TIM validation library and a Spring Boot API that exposes the library over HTTP. The root Maven project is an aggregator, so both modules build together in reactor order.

## Modules

- `timvalidator` - Java library for validating Traveler Information Messages (TIMs).
- `timvalidator-api` - Spring Boot REST API that depends on `timvalidator`.

## Prerequisites

- Java 25 or higher.
- Maven 3.9 or higher; CI and Docker use Maven 3.9.11.
- Docker, optionally, for the containerized API.

## What It Validates

The validator supports TIM MessageFrame payloads in both JER JSON and UPER hex formats. Validation includes:

- Generated J2735 schema validation to confirm the message matches the base J2735 structure.
- ITWG TIM profile schema validation for stricter TIM best-practice constraints.
- ITIS content validation for advisory ITIS code patterns.
- Additional TIM best-practice checks in the validation service.

Validation results are returned as a single response with:

- `valid` - overall pass/fail.
- `issues` - parseable validation issues with severity, check name, message, and JSON Pointer path when available.
- `checks` - status for each validation stage.
- `validationTimestamp` and `validationDurationMs`.

## Build And Test

From the repository root:

```powershell
mvn install
```

To run tests for both modules:

```powershell
mvn -pl timvalidator-api -am clean test
```

The API and validator use the native J2735 codec. The Maven builds copy the native library into each module's `target/libs` directory.

## Run The API

```powershell
cd timvalidator-api
mvn spring-boot:run
```

Default base URL:

```text
http://localhost:8080
```

Main endpoints:

```http
POST /api/v1/tim/validate/jer
Content-Type: application/json
```

```http
POST /api/v1/tim/validate/uper
Content-Type: text/plain
```

JER requests send the TIM MessageFrame JSON body. UPER requests send the UPER-encoded TIM MessageFrame as a hex string.

See [timvalidator-api/README.md](timvalidator-api/README.md) for endpoint details and example response payloads.

### Time Validation

The validator applies two non-blocking time-related best-practice checks to each
TIM data frame. A `startYear` later than the current UTC year produces a warning,
and a `durationTime` of `32000`, which represents an indefinite end time, produces
a warning recommending a definite duration instead.

The current year comes from the UTC system clock by default. Applications validating
archived TIMs can instead supply a historical `java.time.Clock` through
`BestPracticesValidator(Clock)`.

### GNIS Packet-ID Validation

The first three bytes of a nine-byte TIM `packetID` are interpreted as an unsigned,
big-endian GNIS deployment-area identifier. This is a non-blocking best-practice check;
J2735 field validity remains the responsibility of schema validation.

The validator first requires the identifier to exist in its approved Civil GNIS feature
set and retrieves that feature's representative point. It then derives the overall WGS
84 bounds of the TIM's path or circle region geometry and checks whether the point falls
inside an expanded envelope. The expansion is the greater of 50 km or 20 percent of the
TIM bounds' diagonal distance, capped at 150 km. A valid Civil feature outside that search area
receives an unverifiable warning unless it is more than 300 km from the original TIM
bounds, in which case it receives a geographic inconsistency warning. Region anchors are
used only when required to decode relative J2735 path offsets; they are not used as the
representative comparison point.

The reduced Civil GNIS CSV is bundled in the `timvalidator` JAR and loaded once
per JVM. The provider builds an in-memory map for `feature_id` lookups and reads the CSV
directly from the classpath without a temporary file. No GNIS network request, SQLite
dependency, or external GDAL installation is required at runtime. See
[docs/civil_gnis_deployment_areas.md](docs/civil_gnis_deployment_areas.md) for how the
dataset was produced.

### Road-Heading Validation

Roadway-backed heading validation is optional and network-free by default. When it is
enabled and a TIM region has a directional heading slice, the validator queries nearby
OpenStreetMap roadways through Overpass. For a closed path, it decodes the TIM polygon
and sends a slightly buffered version of that polygon as the Overpass search area. For a
circle, it queries from the circle center using the encoded radius and distance unit.
Disabled checks, missing headings, `0000` (no heading), and `ffff` (all headings) do not
trigger a lookup.

Returned roads are projected into the same local coordinate system as the TIM region.
JTS retains only positive-length roadway portions inside the polygon or circle. Each
heading range is checked against every retained segment bearing, treating opposite
bearings as the same road axis. A road that only touches a region at one point does not
qualify.

Heading mismatches, missing road matches, and lookup failures are returned as
non-blocking `WARNING` issues under the `Best Practices` check. The JSON Pointer path
identifies the region heading that was evaluated.

The library fixes a 5-second Overpass query budget and a 20-second HTTP deadline; these
are not configurable. The Overpass endpoint and User-Agent, by contrast, have no default
in the library at all and must be supplied explicitly by the caller — see below.

The Overpass query uses an allowlist of ordinary motor-vehicle road classes from
`motorway` through `tertiary`, along with their link classes, `unclassified`,
`residential`, and `living_street`. Generic `service` and `road` ways are not eligible;
neither are non-road classes such as tracks, paths, cycleways, or pedestrian ways.
Ordinary roads explicitly tagged as inaccessible or private and all area features are
also excluded.

A way tagged `highway=construction` is eligible only when its `construction` tag names
one of those allowed road classes. Temporary `access=no`, `vehicle=no`, or
`motor_vehicle=no` tags are permitted for these construction ways because a closed
work-zone road is still relevant TIM geometry; explicitly private construction ways
and area features remain excluded.

Each heading center may match any roadway segment inside the TIM region. Roads returned
by the buffered polygon query but lying outside the original polygon are discarded
before heading evaluation.

Each contiguous active heading range, including an even-width or north-wrapping
range, is evaluated from its circular angular midpoint. A roadway axis is tangent
when it is within plus or minus 22.5 degrees of that midpoint.

Tests inject an in-memory `RoadGeometryProvider`; they never call the live Overpass service.
The ordinary `new TimValidationService()` constructor is fully network-free: it has no
roadway provider configured at all, so even explicitly passing
`ValidationOptions.withRoadwayHeading()` to it will fail with a clear error rather than
silently reaching any network endpoint.

```java
import us.dot.its.jpo.timvalidator.config.ValidationOptions;

TimValidationService validator = new TimValidationService();

// Guaranteed not to call any roadway provider.
validator.validateTimJer(jer, ValidationOptions.networkFree());
```

To enable roadway-backed heading validation, choose an Overpass endpoint and a User-Agent
that uniquely identifies your application, and use them explicitly — the library has no
default endpoint or User-Agent of its own, since it's published for use by an unknown
number of downstream consumers and must never silently enroll all of them into hitting
shared Overpass infrastructure under one identity. See the Overpass usage guidelines at
<https://wiki.openstreetmap.org/wiki/Overpass_API#Rules_of_usage> before choosing an
endpoint, especially for production or commercial use (self-hosted or paid instances are
recommended there):

```java
TimValidationService validator = TimValidationService.withOverpassRoadGeometry(
    "https://overpass-api.de/api/interpreter",
    "my-app/1.0 (contact@example.com)");

// Enables roadway lookups for directional heading slices.
validator.validateTimJer(jer, ValidationOptions.withRoadwayHeading());
```

The `timvalidator-api` module, being one specific deployment rather than a library used by
unknown consumers, does default to the public Overpass instance and a generic User-Agent —
both configurable via `timvalidator.roadway-heading.overpass-url` and
`timvalidator.roadway-heading.overpass-user-agent` in `application.properties`, and both
should be reviewed for any production deployment.

The API accepts the same choice through `?roadwayHeading=true|false`. Requests that omit
the parameter are network-free by default. Operators can change the default with
`timvalidator.roadway-heading.enabled-by-default`.

Tests and specialized library integrations may still inject a `RoadGeometryProvider`
directly without changing the fixed matching rules.

## Differences: J2735 Schema Vs ITWG Schema

The validator runs both the generated J2735 schema and the stricter ITWG profile schema. The ITWG schema is a best-practice overlay on the generated J2735 TIM MessageFrame schema.

- Base schema: `schemas/TravelerInformation/TravelerInformationMessageFrame.schema.json`
- ITWG schema: `us/dot/its/jpo/timvalidator/TravelerInformationMessageFrameITWG.schema.json`

### Required Fields

`TravelerInformation` requires more fields under ITWG:

- J2735: `msgCnt`, `dataFrames`
- ITWG: `msgCnt`, `dataFrames`, `timeStamp`, `packetID`

Each `dataFrames[]` item requires one additional field under ITWG:

- J2735: `doNotUse1`, `frameType`, `msgId`, `startTime`, `durationTime`, `priority`, `doNotUse2`, `regions`, `doNotUse3`, `doNotUse4`, `content`
- ITWG: same as J2735, plus `startYear`

`contentNew` is optional in both schemas. If `contentNew` is present, it is still validated.

### Closed Objects

The generated J2735 schema generally allows unspecified additional properties. The ITWG schema closes many objects with `additionalProperties: false`, including:

- the root MessageFrame object
- `TravelerInformation`
- each `dataFrames[]` item
- `msgId`
- `content`
- each `regions[]` item and each region shape branch
- nested region objects such as `anchor`, `description`, `geometry`, `circle.center`, and computed lane objects
- nested `contentNew.frictionInfo` objects

This means serializers must omit unsupported fields. Empty arrays still count as present fields, so this fails ITWG validation:

```json
"content": {
  "advisory": [],
  "speedLimit": [],
  "workZone": []
}
```

For ITWG, send only the allowed field:

```json
"content": {
  "advisory": []
}
```

### Removed Or Prohibited Fields

These are valid in the base J2735 structure but are removed or prohibited by the current ITWG schema:

| Path | ITWG behavior |
|---|---|
| `TravelerInformation.urlB` | prohibited |
| `TravelerInformation.regional` | prohibited |
| `dataFrames[].url` | prohibited |
| `dataFrames[].msgId.roadSignID` | prohibited |
| `dataFrames[].content.workZone` | prohibited |
| `dataFrames[].content.genericSign` | prohibited |
| `dataFrames[].content.speedLimit` | prohibited |
| `dataFrames[].content.exitService` | prohibited |
| `regions[].name` | prohibited |
| `regions[].id` | prohibited |
| `regions[].regional` | prohibited |
| `regions[].description.oldRegion` | prohibited |
| `geometry.extent` | prohibited |
| `geometry.laneWidth` | prohibited |
| `geometry.regional` | prohibited |
| `Position3D.regional` on `anchor` or `circle.center` | prohibited |
| `NodeAttributeSetXY/LL.regional` | prohibited |
| `LaneDataAttribute.regional` | prohibited |
| `NodeOffset.regional` | prohibited |
| `ComputedLane.regional` | prohibited |
| `ComputedLane.scaleXaxis` / `ComputedLane.scaleYaxis` | prohibited |

### Choice Narrowing

| Area | J2735 | ITWG |
|---|---|---|
| `frameType` | `unknown`, `advisory`, `roadSignage`, `commercialSignage` | `roadSignage`, `commercialSignage` |
| `msgId` | `furtherInfoID` or `roadSignID` | closed object with only `furtherInfoID` |
| `content` | `advisory`, `workZone`, `genericSign`, `speedLimit`, `exitService` | closed object with only `advisory` |
| `regions[].description` | `path`, `geometry`, `oldRegion` | `path` or `geometry` |

ITWG also requires `msgId.furtherInfoID` to equal `"0000"`.

### Range And Const Tightening

| Field | J2735 | ITWG |
|---|---|---|
| `dataFrames[].doNotUse1` | `0..31` | must be `0` |
| `dataFrames[].doNotUse2` | `0..31` | must be `0` |
| `dataFrames[].doNotUse3` | `0..31` | must be `0` |
| `dataFrames[].doNotUse4` | `0..31` | must be `0` |
| `dataFrames[].durationTime` | `0..32000` | `1..32000` |
| `dataFrames[].startTime` | `0..527040` | `1..527039` |
| `dataFrames[].startYear` | `0..4095` | `2000..4095` |
| `regions[].anchor.lat.maximum` | `900000001` | `900000000` |
| `regions[].anchor.long.maximum` | `1800000001` | `1800000000` |
| `regions[].anchor.required` | `lat`, `long` | `lat`, `long`, `elevation` |
| `regions[].laneWidth.minimum` | `0` | `1` |
| `geometry.circle.center.lat.maximum` | `900000001` | `900000000` |
| `geometry.circle.center.long.maximum` | `1800000001` | `1800000000` |
| `geometry.circle.center.required` | `lat`, `long` | `lat`, `long`, `elevation` |
| `geometry.circle.radius` | `0..4095` | `1..4094` |
| `ComputedLane.referenceLaneId.maximum` | `255` | `254` |

The ITWG schema also adds `ComputedLane` axis-size pairing rules so small offset axes pair with small axes and large offset axes pair with large axes.

### Region Shapes

The base J2735 schema models `regions[]` as one `GeographicalPath` object with several optional fields. ITWG adds three mutually exclusive region shapes:

| Branch | Shape | Required fields | Description allowed |
|---|---|---|---|
| 0 | open path, `closedPath: false` | `anchor`, `laneWidth`, `directionality`, `closedPath`, `description` | `path` |
| 1 | polygon, `closedPath: true` | `anchor`, `closedPath`, `description` | `path` |
| 2 | circle geometry | `description` | `geometry` |

For polygon regions, `direction` is allowed but optional. Use `direction` only when the polygon has a heading restriction. For circle geometry, heading belongs under `description.geometry.direction`; `regions.direction`, `regions.directionality`, and `regions.laneWidth` are not allowed on the circle branch.
