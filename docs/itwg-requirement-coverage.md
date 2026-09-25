# Interoperability Technical Working Group (ITWG) Requirement Coverage

This document maps TIM Validator Tool checks to Interoperability Technical Working
Group (ITWG) TIM best-practice requirements in _Best Practices for TIM Deployment_.
A public link will be added after that document is published. For the end-to-end
validation flow, see [validation-pipeline.md](validation-pipeline.md).

## Status legend

- **Implemented:** Enforced in the current pipeline by schema or code.
- **Partial:** Some aspect is enforced, but not fully aligned with the requirement text.
- **Missing:** Not currently enforced.
- **Deprecated:** The requirement is outdated, incorrect, or superseded and should no longer be implemented.

## Schema Validations

| Requirement | Status | Current Enforcement | Notes / Gap |
|---|---|---|---|
| msgCount field present | Implemented | ITWG schema required list | `msgCnt` required at TravelerInformation level. |
| timeStamp field present | Implemented | ITWG schema required list | Required; integer range `0..527039`. |
| packetID field present | Implemented | ITWG schema required list | Required and hex pattern constrained (9 octets). |
| urlB field should NOT be present | Implemented | ITWG schema additionalProperties false | Unknown properties rejected throughout relevant objects. |
| dataFrames field present | Implemented | ITWG schema required list | Required and min/max items constrained. |
| sspTimRights/doNotUse1 present | Implemented | ITWG schema required list | Required and const `0`. |
| regional should NOT be present | Implemented | ITWG schema additionalProperties false | Disallows unmodeled `regional` fields in constrained objects. |
| frameType field present | Implemented | ITWG schema required list | Required and enum constrained. |
| msgID field present | Implemented | ITWG schema required list | `msgId` required. |
| furtherInfoID field present | Implemented | ITWG schema required list under msgId | `furtherInfoID` required and const `0000`. |
| roadSignID should NOT be present | Implemented | ITWG schema msgId shape | msgId object only permits furtherInfoID. |
| startYear field present | Implemented | ITWG schema required list | Required with range constraint. |
| startTime field present | Implemented | ITWG schema required list | Required with range constraint. |
| durationTime field present | Implemented | ITWG schema required list | Required with range constraint. |
| priority field present | Implemented | ITWG schema required list | Required with range constraint. |
| sspLocationRights/doNotUse2 present | Implemented | ITWG schema required list | Required and const `0`. |
| regions field present | Implemented | ITWG schema required list | Required and min/max items constrained. |
| sspMsgTypes/doNotUse3 present | Implemented | ITWG schema required list | Required and const `0`. |
| sspMsgContent/doNotUse4 present | Implemented | ITWG schema required list | Required and const `0`. |
| content field present | Implemented | ITWG schema required list | Required and advisory-only shape in ITWG profile. |
| url should NOT be present | Implemented | ITWG schema content shape + additionalProperties false | Only advisory content branch is allowed. |
| contentNew field optional | Implemented | ITWG schema omits `contentNew` from the data-frame required list and validates the object when it is present | Matches the draft: Mandatory is crossed out; the field is optional. |
| GeographicPath/name should NOT be present | Implemented | ITWG schema additionalProperties false | `name` not modeled in GeographicalPath branches. |
| GeographicPath/id should NOT be present | Implemented | ITWG schema additionalProperties false | `id` not modeled. |
| GeographicPath/id/region should NOT be present | Implemented | ITWG schema additionalProperties false | `id` branch not modeled. |
| GeographicPath/id/id should NOT be present | Implemented | ITWG schema additionalProperties false | `id` branch not modeled. |
| Anchor field present for paths | Implemented | ITWG schema branch required sets | All ITWG path branches require `anchor`. Circle geometry does not, matching `Anchor required if using paths with offsets`. |
| Anchor.elevation present | Implemented | ITWG schema anchor required list | Required. |
| Anchor.latitude present | Implemented | ITWG schema anchor required list | Required. |
| Anchor.longitude present | Implemented | ITWG schema anchor required list | Required. |

## Basic Message Validations

### General Checks

| Requirement | Status | Current Enforcement | Notes / Gap |
|---|---|---|---|
| timeStamp should not be 527040 | Implemented | ITWG schema max 527039 | Table 1: 527040 is invalid. |
| startTime should not be 527040 | Implemented | ITWG schema max 527039 | Out-of-range value rejected. |
| packetID first 3 bytes match GNIS deployment area | Implemented | GNIS packet-ID geospatial check | Compares the Civil GNIS feature for the packet-ID prefix with the TIM region bounds. |
| dataFrames count 1..8 | Implemented | ITWG schema minItems/maxItems | Enforced by schema. |
| if more than one frame, they must be dependent | Missing | No multi-frame dependency rule | The best practices document currently does not define “dependent.” Needs a clarification before a check can be implemented. |
| doNotUse1 must be 0 | Implemented | ITWG schema const 0 | Enforced. |
| doNotUse2 must be 0 | Implemented | ITWG schema const 0 | Enforced. |
| TravelerInfoType must be roadSignage or commercialSignage | Implemented | ITWG schema enum | Enforced. `advisory` is not allowed. |
| msgID should be furtherInfoID | Implemented | ITWG schema requires furtherInfoID and rejects roadSignID | Aligns with Table 2 “Do not include” `roadSignID`. |
| furtherInfoID should be 0 | Implemented | ITWG schema const `"0000"` | Two-octet hex value. |
| startYear range 2000..4095 | Implemented | ITWG schema min/max | Enforced. |
| startTime range 1..527039 | Implemented | ITWG schema min/max | Enforced. |
| durationTime range 1..32000 | Implemented | ITWG schema min/max | Enforced. |
| durationTime 32000 is allowed but a defined end time is preferred | Implemented | Best-practices warning | Table 2 and §3.3. Warning only; 32000 remains a valid encoded value. |
| priority range 0..7 | Implemented | ITWG schema min/max | Enforced. |
| UTC-only time usage | Missing | No timezone semantics check | Table 2: “All times based on UTC.” Payload stores minute-of-year integers only. Out of scope for the core library. |
| regions up to 16 geographical paths | Implemented | ITWG schema maxItems 16 | Enforced. |
| doNotUse3 must be 0 | Implemented | ITWG schema const 0 | Enforced. |
| doNotUse4 must be 0 | Implemented | ITWG schema const 0 | Enforced. |
| content must be advisory and not workzone/genericSign/speedLimit/exitService/url | Implemented | ITWG schema content requires advisory | Profile restricts content choice to advisory branch. |
| Path + closedPath=false: directionality populated, direction not populated | Implemented | ITWG branch for `closedPath=false` requires directionality and omits direction | Enforced by branch shape + additionalProperties false. |
| Path + closedPath=true: directionality not populated | Implemented | ITWG closed-path branch omits directionality | Matches Table 3. `direction` is optional in the draft when a heading restriction exists; it is not required. |
| Circle: regions.direction and directionality should NOT be populated | Implemented | ITWG circle branch omits those fields | AdditionalProperties false blocks extras in that branch. |
| Circle: geometry.direction should be populated | Implemented | ITWG circle branch requires direction | Enforced by geometry required list. |
| Description should not be oldRegion | Implemented | ITWG description choice excludes oldRegion | Only path or geometry allowed in profile branches. |

### Anchor Checks

| Requirement | Status | Current Enforcement | Notes / Gap |
|---|---|---|---|
| Anchor required if using path with offsets | Implemented | Open and closed paths require anchor; circle geometry does not | Enforced. |
| Elevation required if using anchor | Implemented | ITWG anchor required list | Enforced. |
| Latitude range -900000000..900000000 | Implemented | ITWG schema min/max | Enforced. |
| Longitude range -1799999999..1800000000 | Implemented | ITWG schema min/max | Enforced. |
| Elevation range -4095..61439 or -4096 unknown | Implemented | ITWG allows -4096..61439 | Permits -4096 for unknown and the valid range. |
| Anchor should be 10 m before the first node | Implemented | Geometry check | Table 3. Implementation uses a 10 m ±1 m window. |

### Lane Width Checks

| Requirement | Status | Current Enforcement | Notes / Gap |
|---|---|---|---|
| laneWidth required for path with closedPath=false | Implemented | ITWG open-path branch required set | Enforced. |
| laneWidth not set for closedPath=true | Implemented | ITWG closed-path branch omits laneWidth | Enforced via additionalProperties false. |
| laneWidth not set for circle TIM | Implemented | ITWG circle branch omits laneWidth | Matches Table 7 “Do not use” on GeometricProjection.laneWidth. |
| laneWidth range 1..32767 | Implemented | ITWG schema min/max | J2735 units are centimeters (max 327.67 m). |
| laneWidth cannot be 0 | Implemented | ITWG schema min laneWidth=1 | Enforced. |

### Path / Geometry / Node / Computed-Lane Checks

| Requirement | Status | Current Enforcement | Notes / Gap |
|---|---|---|---|
| path required when using Path TIM type | Implemented | ITWG path branches require description.path | Enforced. |
| OffsetSystem.scale Zoom 0..15 | Implemented | ITWG schema min/max | Table 5. |
| choose XY vs LL offsets based on size thresholds | Implemented | Calculates path separation and warns when XY, LL, or absolute longitude/latitude nodes would be more appropriate | Table 5: XY for most paths; LL for long-distance paths. |
| geometricProjection required for circle type | Implemented | ITWG circle branch requires description.geometry | Enforced. |
| circle path type: laneWidth not specified | Implemented | ITWG circle branch excludes laneWidth | Enforced. |
| circle path type: extent not specified | Implemented | ITWG circle schema excludes extent | Enforced by shape restrictions. |
| circle path type: regional not specified | Implemented | ITWG circle schema excludes regional | Enforced by shape restrictions. |
| circle center lat required | Implemented | ITWG schema required list | Enforced. |
| circle center long required | Implemented | ITWG schema required list | Enforced. |
| circle center elevation required | Implemented | ITWG schema required list | Enforced. |
| circle radius required | Implemented | ITWG schema required list | Enforced. |
| circle radius 1..4094, 4095 invalid | Implemented | ITWG schema max 4094 | Enforced. |
| circle units required | Implemented | ITWG schema required list | Enforced. |
| should require metric units | Implemented | Warning if imperial units are used | Table 7 recommendation. |
| NodeSetXY min 2 max 63 | Implemented | ITWG schema minItems/maxItems | Enforced. |
| NodeXY delta required | Implemented | ITWG schema required list for NodeXY | Enforced. |
| Node attributes only if needed | Implemented | Rejects empty attributes and zero `dWidth` / `dElevation` | Table 6: only use if attributes are needed. |
| ComputedLane offsetXAxis required | Implemented | ITWG schema required set | Enforced. |
| ComputedLane offsetYAxis required | Implemented | ITWG schema required set | Enforced. |
| ComputedLane referenceLaneId required 0..254 | Implemented | ITWG schema required and bounds | Enforced. |
| left-most reference lane recommendation | Implemented | Warning on computed lanes | Table 6 recommendation. |
| offsetXAxis numeric ranges | Implemented | ITWG schema oneOf range constraints | Enforced. |
| offsetYAxis numeric ranges | Implemented | ITWG schema oneOf range constraints | Enforced. |
| rotateXY optional, range 0..28800 if present | Implemented | ITWG schema min/max | Enforced. |
| scaleXAxis do not include | Implemented | ITWG schema computed lane model excludes scaleXAxis | Enforced via additionalProperties false. |
| scaleYAxis do not include | Implemented | ITWG schema computed lane model excludes scaleYAxis | Enforced via additionalProperties false. |
| regional do not include (computed lane context) | Implemented | ITWG schema excludes field | Enforced via additionalProperties false. |
| heading slice tangent to roadway at start | Implemented | Optional check using the OpenStreetMap Overpass API | Table 7. Network-free by default. |
| do not use extent in heading slice context | Implemented | ITWG constrained object shapes | Extent field not modeled in allowed branches. |
| If no heading restriction, set heading slice to all ones | Missing | `ffff` / all-ones is accepted but not required | Table 7. |

## Deployment Specific Checks

| Requirement | Status | Current Enforcement | Notes / Gap |
|---|---|---|---|
| ITIS code validation | Implemented | ItisJsonValidator + ITISCodes schema | Pattern-based sequence validation exists for supported patterns in ITISCodes.json. Table 4 phrase construction, MUTCD placement, and “ahead”/distance wording are not encoded rules. |
| TIM priority validation against deployment/vendor policy | Missing | No policy mapping logic | §3.1 sample 7/6/4/2/0 scheme. Requires external policy. |
| msgCnt starts at 0 for a new TIM; increments by 1 (modulo 127) when content changes | Missing | No message history | Table 1. Must be checked in an external stateful application. |
| packetID must not change when TIM content is updated | Missing | No message history | Table 1. External stateful check. |
| timeStamp must be updated whenever msgCnt is updated | Missing | No message history | Table 1. External stateful check. |
| TravelerInfoType must be `roadSignage` for TIMs generated by a state or local agency | Missing | No policy | This must be checked by the agency generating the TIM. |
| TravelerInfoType must be `commercialSignage` for TIMs generated by a commercial entity | Missing | No policy | This must be checked by the agency generating the TIM. |

## Geospatial Checks

Draft-aligned geometry checks. Self-intersection, repeated points, and closed-ring
rules are treated as consequences of a usable path or polygon in Table 3 / §3.2,
not as extra TIM fields.

| Requirement | Status | Current Enforcement | Notes / Gap |
|---|---|---|---|
| open path laneWidth must produce resolvable polygon geometry | Implemented | Detects when boundaries intersect, form an invalid polygon, or have unusable area | Aligns with Table 3: laneWidth consistent with centerline nodes. |
| polygon expansion consistency rules | Implemented | Checks the maximum width at bends and validates the expanded corridor's boundaries | Added in geometry validation. |
| line geometries should not self-intersect | Implemented | Open paths are checked for self-intersection using JTS line simplicity | Added in geometry validation. |
| paths should not have repeated points | Implemented | Repeated nodes in open paths produce an error identifying the duplicate indexes | Added in geometry validation. |
| closed path polygon should not self-intersect | Implemented | Closed polygon boundaries are checked for intersection | Added in geometry validation. |
| polygons should have at least 4 points | Implemented | ITWG schema requires at least four XY or LL nodes | Closed path needs a ring. |
| first and last polygon points should coincide | Implemented | Closed paths produce an error when the endpoints do not coincide | Added in geometry validation. |
| polygon should not have repeated points | Implemented | Repeated polygon vertices cause an error, excluding the first/last closing point | Added in geometry validation. |
| Geofence covers the full incident length; width should not extend significantly beyond the roadway | Missing | No roadway-edge or incident-extent check | §3.2 CTW points A–E. Needs roadway context. |
| Advance-warning geofence ends where the main TIM geofence begins | Missing | No multi-TIM geofence pairing | §2.4.1. |
| Node density high enough to stay on the roadway; circle radius large enough | Missing | No density or “large enough” radius rule | Table 3 qualitative guidance. |
