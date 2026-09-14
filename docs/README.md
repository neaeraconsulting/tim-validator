# TIM Validator Tool Documentation

This directory contains technical documentation for the TIM Validator Tool. Start
with the [repository README](../README.md) for build instructions, prerequisites,
and usage examples.

## Recommended reading order

### New integrators

1. [Validation pipeline](validation-pipeline.md) — how input becomes a validation result
2. [J2735 vs ITWG schema](j2735-vs-itwg-schema.md) — what the two schema stages enforce
3. [Validation checks](validation-checks.md) — behavioral rules beyond schema validation
4. [API README](../timvalidator-api/README.md) — if using the REST API

### Schema and standards reviewers

1. [J2735 vs ITWG schema](j2735-vs-itwg-schema.md)
2. [ITWG requirement coverage](itwg-requirement-coverage.md)
3. [Best Practices for TIM Deployment (draft PDF)](Best%20Practices%20for%20TIM%20Deployment%20DRAFT%20(Consolidated%20Content%20Types)%2008-20-2026.pdf)

### Operators validating messages

1. [Validation pipeline](validation-pipeline.md) — severity model and example payloads
2. [Validation checks](validation-checks.md) — especially GNIS and roadway-heading configuration
3. [API README](../timvalidator-api/README.md)

## Document index

| Document | Purpose |
|---|---|
| [validation-pipeline.md](validation-pipeline.md) | End-to-end pipeline, module architecture, severity model, and example fixtures |
| [validation-checks.md](validation-checks.md) | Time, GNIS, geometry, and roadway-heading check behavior |
| [j2735-vs-itwg-schema.md](j2735-vs-itwg-schema.md) | Required fields, prohibited fields, and choice narrowing between schemas |
| [itwg-requirement-coverage.md](itwg-requirement-coverage.md) | ITWG best-practice requirement implementation status |
| [civil_gnis_deployment_areas.md](civil_gnis_deployment_areas.md) | How the bundled Civil GNIS CSV was produced |
| [Best Practices for TIM Deployment (reference draft PDF)](Best%20Practices%20for%20TIM%20Deployment%20DRAFT%20(Consolidated%20Content%20Types)%2008-20-2026.pdf) | ITWG reference draft used for this release's traceability; not a final standard |

## Example payloads

JER MessageFrame test fixtures live under
`timvalidator/src/test/resources/fixtures/`. See the
[example payloads section](validation-pipeline.md#full-pipeline-valid-examples) in the
validation pipeline document for a fixture catalog and curl examples.
