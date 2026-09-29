# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] — 2026-09-28

### First Release

- `timvalidator` Java library for validating SAE J2735 Traveler Information Message (TIM) MessageFrames in JER JSON and UPER hex.
- Generated J2735 schema validation and a stricter Interoperability Technical Working Group (ITWG) TIM profile schema overlay.
- ITIS advisory content validation.
- Best-practice checks for TIM start year, indefinite duration, GNIS `packetID` deployment-area identifiers, region geometry, and optional OpenStreetMap roadway heading.
- Packaged Civil GNIS deployment-area CSV for offline `packetID` lookups.
- `timvalidator-api` Spring Boot REST API exposing JER and UPER validation endpoints.
- Docker image and Compose service for the API.
- Maven reactor aggregator so both modules build together.

[1.0.0]: https://github.com/neaeraconsulting/tim-validator/tree/1.0.0
