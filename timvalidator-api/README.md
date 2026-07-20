# TIM Validator API

Spring Boot wrapper for the `timvalidator` library.

## Run Locally

Install the validator jar first:

```powershell
cd ..\timvalidator
mvn install "-Dmaven.test.skip=true"
```

Start the API:

```powershell
cd ..\timvalidator-api
mvn spring-boot:run
```

The Spring Boot Maven plugin is configured with `--enable-native-access=ALL-UNNAMED`, which the native J2735 codec needs on modern Java.

The API build copies the native ASN codec to `target/libs`. If you run the packaged jar from another directory, copy the matching native file beside the jar or into a `libs` directory:

```text
asnapplication.dll      Windows
libasnapplication.so    Linux
```

Packaged jar example:

```powershell
java --enable-native-access=ALL-UNNAMED -jar target\timvalidator-api-1.0.0-SNAPSHOT.jar
```

Default base URL:

```text
http://localhost:8080
```

## JER Validation Endpoint

```http
POST /api/v1/tim/validate/jer
Content-Type: application/json
Accept: application/json
```

Send the TIM MessageFrame as JER JSON in the raw request body. The endpoint accepts either an unwrapped MessageFrame JSON object or a wrapper shaped like `{ "MessageFrame": { ... } }`.

## UPER Validation Endpoint

```http
POST /api/v1/tim/validate/uper
Content-Type: text/plain
Accept: application/json
```

Send the UPER-encoded TIM MessageFrame as a hexadecimal string in the raw request body.

## Response Shape

```json
{
  "valid": false,
  "issues": [
    {
      "severity": "ERROR",
      "checkName": "ITWG Schema Validation",
      "message": "must have a maximum value of 7",
      "path": "/value/TravelerInformation/dataFrames/0/priority"
    },
    {
      "severity": "WARNING",
      "checkName": "ITIS Content Validation",
      "message": "No data frame contained numeric advisory ITIS codes eligible for ITIS pattern validation.",
      "path": "/value/TravelerInformation/dataFrames"
    }
  ],
  "checks": [
    {
      "name": "J2735 Schema Validation",
      "passed": true,
      "details": "Message conforms to generated J2735 schema"
    },
    {
      "name": "ITWG Schema Validation",
      "passed": false,
      "details": "Schema validation failed: ..."
    }
  ],
  "validationTimestamp": "2026-06-23T19:00:00Z",
  "validationDurationMs": 42
}
```

Validation failures return `200 OK` with `valid: false` when the API can parse and process the request. Invalid request bodies return `400 Bad Request`.

Schema checks are split into the generated J2735 schema and the stricter ITWG TIM profile schema:

```text
J2735 Schema Validation
ITWG Schema Validation
```

Issue paths are JSON Pointer paths or `null`. Examples:

```text
/messageId
/value/TravelerInformation/dataFrames/0/priority
/value/TravelerInformation/dataFrames/0/content/advisory
/itis
null
```
