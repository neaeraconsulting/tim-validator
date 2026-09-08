# TIM Validation Checks

This document describes the library and API validation pipeline beyond the generated
J2735 and Interoperability Technical Working Group (ITWG) JSON Schema stages. It
preserves the operational detail previously documented in the repository README.

Validation results are returned as a single response with:

- `valid` — overall pass/fail.
- `issues` — parseable validation issues with severity, check name, message, and JSON Pointer path when available.
- `checks` — status for each validation stage.
- `validationTimestamp` and `validationDurationMs`.

## Time Validation

The validator applies two time-related best-practice checks to each TIM data
frame. A `startYear` later than the current UTC year produces a warning. A
`durationTime` of `32000`, which represents an indefinite end time, ordinarily
produces a warning recommending a definite duration instead.

When the data frame's numeric advisory content contains ITIS code `6952`
(`LOOK_OUT_FOR_WORKERS`), an indefinite `durationTime` is an error. Worker-related
TIMs must use a finite, limited time window. The check applies to the data frame
containing the worker code; other data frames in the same TIM are evaluated from
their own advisory content.

The current year comes from the UTC system clock by default. Applications validating
archived TIMs can instead supply a historical `java.time.Clock` through
`BestPracticesValidator(Clock)`.

## GNIS Packet-ID Validation

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
[civil_gnis_deployment_areas.md](civil_gnis_deployment_areas.md) for how the
dataset was produced.

## Road-Heading Validation

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
