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

## Response Shape

```json
{
  "valid": false,
  "errors": [
    "Schema Validation: Schema validation failed: ..."
  ],
  "warnings": [
    "Best Practices: ..."
  ],
  "fieldErrors": [
    {
      "path": "/value/TravelerInformation/dataFrames/0/priority",
      "message": "must have a maximum value of 7"
    }
  ],
  "checks": [
    {
      "name": "Schema Validation",
      "passed": false,
      "details": "Schema validation failed: ..."
    }
  ],
  "validationTimestamp": "2026-06-23T19:00:00Z",
  "validationDurationMs": 42
}
```

Validation failures return `200 OK` with `valid: false` when the API can parse and process the request. Invalid request bodies return `400 Bad Request`.
