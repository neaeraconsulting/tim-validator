# TIM Validator Tool

## Project Description

TIM Validator Tool is a U.S. Department of Transportation (U.S. DOT) Intelligent
Transportation Systems Joint Program Office (ITS JPO) project. Noblis provides
systems engineering. Neaera Consulting developed this source code.

The software is a Java library and Spring Boot REST API for validating SAE J2735
Traveler Information Messages (TIMs). TIMs carry roadway and traveler information
similar to road signs or dynamic message signs, including work-zone, speed, and
event notices for connected and automated vehicle applications.

The purpose of this source code is to give ITS applications a reusable validator
for TIM MessageFrame payloads in both JER JSON and UPER hex. Validation confirms
that a message matches the generated J2735 structure, then applies a stricter
Interoperability Technical Working Group (ITWG) TIM profile schema, ITIS advisory
content checks, and additional TIM best-practice checks. Those practices follow
_Best Practices for TIM Deployment_, developed by Justin Anderson. A public link
will be added after that document is published. The library is the core product; the
API is a thin HTTP wrapper over the same service so operators can validate messages
without embedding the library.

This repository is a Maven aggregator with two modules that build together in
reactor order:

- `timvalidator` — Java library for validating TIM MessageFrames.
- `timvalidator-api` — Spring Boot REST API that depends on `timvalidator`.

The library uses the native J2735 2024 ASN.1 codec. It is related to that codec
and to the packaged Civil GNIS deployment-area dataset used for `packetID`
checks. The public source repository is
[https://github.com/neaeraconsulting/tim-validator](https://github.com/neaeraconsulting/tim-validator).

## Prerequisites

Requires:

- Java 25 (or higher)
- Maven 3.9 or higher (3.9.11 is used in CI and Docker)
- Docker (optional, for the containerized API)

The Maven builds copy the native J2735 codec into each module's `target/libs`
directory:

| Platform | Native library |
|---|---|
| Windows | `asnapplication.dll` |
| Linux | `libasnapplication.so` |

If you run the packaged API jar from another directory, copy the matching native
file beside the jar or into a `libs` directory. The Spring Boot Maven plugin
passes `--enable-native-access=ALL-UNNAMED`, which the native codec needs on
modern Java.

## Usage

### Building

From the repository root, install both modules:

```bash
mvn install
```

On Windows PowerShell the same command works:

```powershell
mvn install
```

Build the API Docker image:

```bash
docker build -t timvalidator-api .
```

Or build and start the API with Compose:

```bash
docker compose up --build
```

### Testing

Unit tests and JaCoCo coverage checks run as part of the Maven build. From the
repository root:

```bash
mvn -pl timvalidator-api -am clean test
```

To compile, test, and enforce coverage thresholds (80% line / 60% branch on the
library):

```bash
mvn clean verify
```

Library tests inject in-memory fakes for roadway geometry. They never call the
live Overpass service.

### Execution

#### Library

The library includes a Java JAR and depends on the FFM native library, both available from Maven 
Central. To consume the published JAR, add artifact the dependency:

```xml
<dependency>
  <groupId>com.neaeraconsulting</groupId>
  <artifactId>timvalidator</artifactId>
  <version>1.0.0</version>
</dependency>
```
and also add the following to build/plugins in the POM to copy the native libraries for Linux
and Windows:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <version>3.6.1</version>
    <executions>
        <execution>
            <id>copy-native-windows-x86_64</id>
            <phase>process-resources</phase>
            <goals>
                <goal>copy</goal>
            </goals>
            <configuration>
                <artifactItems>
                    <artifactItem>
                        <groupId>com.neaeraconsulting</groupId>
                        <artifactId>j2735-2024-ffm-lib</artifactId>
                        <version>3.0.0-beta1</version>
                        <classifier>windows-x86_64</classifier>
                        <type>dll</type>
                        <destFileName>asnapplication.dll</destFileName>
                    </artifactItem>
                </artifactItems>
                <outputDirectory>${project.build.directory}/libs</outputDirectory>
            </configuration>
        </execution>
        <execution>
            <id>copy-native-linux-x86_64</id>
            <phase>process-resources</phase>
            <goals>
                <goal>copy</goal>
            </goals>
            <configuration>
                <artifactItems>
                    <artifactItem>
                        <groupId>com.neaeraconsulting</groupId>
                        <artifactId>j2735-2024-ffm-lib</artifactId>
                        <version>3.0.0-beta1</version>
                        <classifier>linux-x86_64</classifier>
                        <type>so</type>
                        <destFileName>libasnapplication.so</destFileName>
                    </artifactItem>
                </artifactItems>
                <outputDirectory>${project.build.directory}/libs</outputDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

```java
import us.dot.its.jpo.timvalidator.config.ValidationOptions;
import us.dot.its.jpo.timvalidator.service.TimValidationService;

TimValidationService validator = new TimValidationService();

// Network-free: J2735 schema, ITWG schema, ITIS, and local best-practice checks.
validator.validateTimJer(jer, ValidationOptions.networkFree());
```

To enable optional OpenStreetMap roadway-heading checks, supply an Overpass
endpoint and a User-Agent that uniquely identifies your application. The library
has no default endpoint or User-Agent. See the
[Overpass usage guidelines](https://wiki.openstreetmap.org/wiki/Overpass_API#Rules_of_usage)
before choosing an endpoint:

```java
TimValidationService validator = TimValidationService.withOverpassRoadGeometry(
    "https://overpass-api.de/api/interpreter",
    "my-app/1.0 (contact@example.com)");

validator.validateTimJer(jer, ValidationOptions.withRoadwayHeading());
```

#### REST API

Run from source after installing the library:

```bash
cd timvalidator-api
mvn spring-boot:run
```

Run the packaged jar (place the native library beside the jar or in `libs/`):

```bash
java --enable-native-access=ALL-UNNAMED -jar target/timvalidator-api-1.0.0.jar
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

JER requests send the TIM MessageFrame JSON body. UPER requests send the
UPER-encoded TIM MessageFrame as a hex string. Add `?roadwayHeading=true` to
opt into the optional roadway-heading check for that request.

Example:

```bash
curl -sS -X POST http://localhost:8080/api/v1/tim/validate/jer \
  -H "Content-Type: application/json" \
  --data-binary @tim-messageframe.json
```

See [timvalidator-api/README.md](timvalidator-api/README.md) for endpoint details,
response payloads, and native-library packaging notes.

## Additional Notes

**Further documentation:**

- [Documentation index](docs/README.md) — recommended reading order and document catalog.
- [Validation pipeline](docs/validation-pipeline.md) — end-to-end pipeline diagram, module architecture, severity model, and example payloads.
- [ITWG requirement coverage](docs/itwg-requirement-coverage.md) — implemented, partial, missing, and deprecated checks.
- [Validation checks](docs/validation-checks.md) — time, GNIS `packetID`, geometry, and roadway-heading behavior, including library configuration examples.
- [J2735 vs ITWG schema](docs/j2735-vs-itwg-schema.md) — required fields, prohibited fields, choice narrowing, and region-shape rules.
- [Civil GNIS deployment areas](docs/civil_gnis_deployment_areas.md) — how the packaged GNIS extract was produced.
- [API README](timvalidator-api/README.md) — REST request and response details.
- _Best Practices for TIM Deployment_ — ITWG best-practices document used for this release’s traceability. A public link will be added after publication.

**Roadway heading:** The library constructor is network-free. The API defaults
to the public Overpass instance and a generic User-Agent when heading checks are
enabled; review `timvalidator.roadway-heading.overpass-url` and
`timvalidator.roadway-heading.overpass-user-agent` before production use.
Requests that omit `?roadwayHeading` are network-free unless
`timvalidator.roadway-heading.enabled-by-default` is set to `true`.

**Associated datasets:** A reduced USGS Geographic Names Information System
(GNIS) Civil feature extract is bundled in the `timvalidator` JAR for offline
`packetID` deployment-area checks. Source data is the USGS Domestic Names
national file from the
[GNIS download page](https://www.usgs.gov/us-board-on-geographic-names/download-gnis-data).
GNIS Domestic Names data is a U.S. Government work. No GNIS network request,
SQLite dependency, or GDAL installation is required at runtime.

When optional roadway-heading validation is enabled, the tool queries
OpenStreetMap through the Overpass API. OpenStreetMap data is © OpenStreetMap
contributors and is available under the
[Open Data Commons Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/).
Public Overpass instances are shared, rate-limited infrastructure; follow the
[Overpass usage guidelines](https://wiki.openstreetmap.org/wiki/Overpass_API#Rules_of_usage)
and identify your application with a unique User-Agent.

**Known limitations:** Some ITWG deployment-policy checks are intentionally out
of scope for this library. See
[ITWG requirement coverage](docs/itwg-requirement-coverage.md) for the full
status list. Currently **Missing** items include multi-frame dependency,
UTC-only time semantics, vendor/deployment priority policy, `msgCnt` increment
across content changes, and agency-versus-commercial `frameType` policy.

## Version History and Retention

**Status:** This project is in the release phase (`1.0.0`).

**Release Frequency:** This project is updated as needed.

**Release History:** See [CHANGELOG.md](CHANGELOG.md).

**Retention:** This project will remain publicly accessible for a minimum of five
years (until at least 09/08/2031).

## License

This project is licensed under the Apache License, Version 2.0 — see
[LICENSE.md](LICENSE.md) for the licensing status and full license text.
Copyright 2026 U.S. Department of Transportation (U.S. DOT); see [NOTICE](NOTICE).

## Contributions

Community pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md)
for details on our Code of Conduct, the process for submitting pull requests,
and how contributions will be released.

## Contact Information

Contact Name: Spain Niemer, Systems Engineer, Noblis

Contact Information: Spain.Niemer@noblis.org

Contact Name: Kellen Shain, Systems Engineer, Noblis

Contact Information: Kellen.Shain@noblis.org

## Acknowledgements

### Citing this code

If you build additional software using this code, please acknowledge the source
repository in your software's README or documentation.

Published Maven artifact:

> `com.neaeraconsulting:timvalidator:1.0.0`

To cite this code in a publication or report:

> Noblis. (2026). _TIM Validator Tool_ (1.0.0) [Source code]. Provided by ITS CodeHub through GitHub.com. Accessed YYYY-MM-DD from https://github.com/neaeraconsulting/tim-validator.

When you copy or adapt from this code, please include the original URL you
copied the source code from and date of retrieval as a comment in your code.
Additional information on how to cite can be found in the
[ITS CodeHub FAQ](https://its.dot.gov/code/#/faqs).

### Contributors

- Spain Niemer, Systems Engineer, Noblis
- Kellen Shain, Systems Engineer, Noblis
- Software development team, Neaera Consulting: Michael English, Drew Johnston, John Wiens, Ivan Yourshaw, Rishabh Kapoor, and Darren Weibler

_Best Practices for TIM Deployment_ was developed by Justin Anderson. A public
link will be added after the document is published.

This project is sponsored by the ITS Joint Program Office, U.S. Department of
Transportation. Noblis provides systems engineering. Software implementation
was performed by Neaera Consulting.
