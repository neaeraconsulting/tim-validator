package us.dot.its.jpo.timvalidator.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import org.locationtech.jts.geom.Coordinate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.dot.its.jpo.timvalidator.converter.JerToMessageFrameConverter;
import us.dot.its.jpo.timvalidator.converter.UperToMessageFrameConverter;
import us.dot.its.jpo.timvalidator.config.ValidationOptions;
import us.dot.its.jpo.timvalidator.exception.RoadGeometryLookupException;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.pojo.ValidationResult;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.road.RoadGeometryProvider;
import us.dot.its.jpo.timvalidator.road.RoadSegment;
import us.dot.its.jpo.timvalidator.validator.BestPracticesValidator;
import us.dot.its.jpo.timvalidator.validator.ItwgTimJsonValidator;
import us.dot.its.jpo.timvalidator.validator.TimJsonValidator;

/**
 * Basic test suite to verify Maven build and project structure.
 */
public class TimValidationServiceTest {

    private static final String ITWG_VALID_UPER_HEX =
        "001f3c6010000100000000000000000018080000fd40000200f2000424d693a401ad2747fc40008000026b49d200d693a3fe2000002c000000218020402200";
    private static final String FULLY_VALID_UPER_HEX =
        "001f3e6010000100000000000000000018080000fd40000200f2000424d693a401ad2747fc40008000026b49d200d693a3fe2000002c0000440080308010201100";
    private static final String COMPLEX_VALID_UPER_HEX =
        "001f5a60050d291a05652359a6ea9dce18080000fd29ec31f4020007a5270dcdf0e3ee6d5c00002ee208705f440006c38df5c36dadfe21c526c110eaeb4520ac11e3f2c3d6959081d706e9a150b04c3800001004306204221001020110";
    private static final String LEGACY_UPER_MISSING_ITWG_FIELDS_HEX =
        "001F6970138ED764E8ABE0BBA9B4D5240F775D9B0309C269A6E4D166420B77FFF93F51D3C5801EA107F92937E4AD64D6FD38352FB783062C360DE24000000004D34DC9A2CC8416E271180004420C0F23A84179FF2461BE25D59F405F03B8C82F1574AE109002009EEEBB36006001830002848A859B4B280002848AF0E51D2881010100030180C620FB90CAAD3B9C50820826550919D5729A7639692100032A3649C88400A983010180034801010001838182D6DDACDEEEE30D5990CA8E531F4562161223F5418FD9A82BE7219686AA70CD938080BE6942DDAC14F4007CC8F8BD6CAEA835F02C7BBA3354ED2856E5977879ECEF5205A37A1CD9A26E12A6CFF6550202138D3F5CA0D3AE158B18895F0BBF16176971";

    private TimValidationService validationService;

    @BeforeEach
    public void setUp() {
        validationService = new TimValidationService();
    }

    @Test
    public void testServiceInstantiation() {
        assertNotNull(validationService, "Service should be instantiated successfully");
    }

    @Test
    public void testValidationResultCreation() {
        ValidationResult result = new ValidationResult();
        assertNotNull(result, "ValidationResult should be created");
        assertTrue(result.isValid(), "New ValidationResult should be valid by default");
    }

    @Test
    public void testValidationCheckAddition() {
        ValidationResult result = new ValidationResult();
        result.addValidationCheck("Test Check", true, "Test passed");
        
        assertNotNull(result.getValidationChecks(), "Validation checks should not be null");
        assertTrue(result.getValidationChecks().containsKey("Test Check"), 
                   "Test check should be added to results");
    }

    @Test
    public void testValidationWarningAddition() {
        ValidationResult result = new ValidationResult();
        result.addWarning("Test Check", "Test warning", "/test/path");

        assertNotNull(result.getWarnings(), "Validation warnings should not be null");
        assertTrue(result.getWarnings().stream().anyMatch(issue -> issue.message().equals("Test warning")
            && issue.path().equals("/test/path")),
                   "Test warning should be added to results");
        assertTrue(result.isValid(), "Warnings should not make the result invalid");
    }

    @Test
    public void testValidationErrorMakesResultInvalid() {
        ValidationResult result = new ValidationResult();
        result.addError("Test Check", "Test error", "/test/path");

        assertFalse(result.isValid(), "Errors should make the result invalid");
    }

    @Test
    public void testValidationResultSummary() {
        ValidationResult result = new ValidationResult();
        result.addValidationCheck("Schema Validation", true, "Schema check passed");
        result.addValidationCheck("Best Practices", true, "All best practices passed");
        result.addWarning("Best Practices", "Warning detail", "/warning/path");
        
        String summary = result.getSummary();
        assertNotNull(summary, "Summary should be generated");
        assertTrue(summary.contains("VALID"), "Summary should contain validation status");
        assertTrue(summary.contains("Schema Validation"), "Summary should contain check names");
        assertTrue(summary.contains("Warning detail"), "Summary should contain warning details");
    }

    public void testValidationException() {
        ValidationException ex = assertThrows(ValidationException.class, () -> {
            throw new ValidationException("Test exception");
        }, "ValidationException should be throwable");
        assertTrue(ex.getMessage().contains("Test exception"));
    }

    @Test
    public void testJerValidationFailureIncludesResult() {
        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> validationService.validateTimJer("{}"),
            "Unrecognized JER shape should produce a validation exception"
        );

        assertNotNull(ex.getValidationResult(), "ValidationException should include validation result");
        assertFalse(ex.getValidationResult().isValid(), "Validation result should be invalid");
        assertFalse(ex.getValidationResult().getValidationChecks().isEmpty(), "Validation checks should be populated");
    }

    @Test
    public void validateTimJer_parseableInvalidMessage_returnsAllValidationChecks() throws Exception {
        ValidationResult result = validationService.validateTimJer("{\"messageId\":31}");

        assertFalse(result.isValid(), "Invalid parseable JER should return an invalid validation result");
        assertTrue(result.getValidationChecks().containsKey("J2735 Schema Validation"),
            "J2735 schema check should run");
        assertTrue(result.getValidationChecks().containsKey("ITWG Schema Validation"),
            "ITWG schema check should run after J2735 failure");
        assertTrue(result.getValidationChecks().containsKey("ITIS Content Validation"),
            "ITIS check should run after schema failures");
        assertTrue(result.getValidationChecks().containsKey("Best Practices"),
            "Best Practices should run after earlier failures");
        assertFalse(result.getValidationChecks().get("J2735 Schema Validation").isPassed());
        assertFalse(result.getValidationChecks().get("ITWG Schema Validation").isPassed());
    }

    @Test
    public void testJerToMessageFrameConverterInstantiation() {
        JerToMessageFrameConverter converter = new JerToMessageFrameConverter();
        assertNotNull(converter, "JerToMessageFrameConverter should be instantiated");
    }

    @Test
    public void testUperToMessageFrameConverterInstantiation() {
        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();
        assertNotNull(converter, "UperToMessageFrameConverter should be instantiated");
    }

    @Test
    public void testTimJsonValidatorInstantiation() {
        TimJsonValidator validator = new TimJsonValidator();
        assertNotNull(validator, "TimJsonValidator should be instantiated");
    }

    @Test
    public void testItwgTimJsonValidatorInstantiation() {
        ItwgTimJsonValidator validator = new ItwgTimJsonValidator();
        assertNotNull(validator, "ItwgTimJsonValidator should be instantiated");
    }

    @Test
    public void testBestPracticesValidatorInstantiation() {
        BestPracticesValidator validator = new BestPracticesValidator();
        assertNotNull(validator, "BestPracticesValidator should be instantiated");
    }

    @Test
    public void testStandardOverpassServiceInstantiation() {
        TimValidationService validator = TimValidationService.withOverpassRoadGeometry();
        assertNotNull(validator, "Overpass-backed service should be instantiated");
    }

    @Test
    public void testBestPracticesValidatorNullHandling() throws ValidationException {
        BestPracticesValidator validator = new BestPracticesValidator();
        var issues = validator.validate(null);
        
        assertNotNull(issues, "Issues list should not be null");
        assertFalse(issues.isEmpty(), "Should report issues for null message");
    }

    @Test
    public void validateTimJer_headingMismatchReturnsStructuredBestPracticesWarning() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        TimValidationService roadBackedService = new TimValidationService(
            (location, radius) -> {
                providerCalls.incrementAndGet();
                return java.util.List.of(RoadSegment.validRoadSegment(
                    202L,
                    "Broadway",
                    java.util.List.of(
                        new Coordinate(-0.001, 0.0),
                        new Coordinate(0.001, 0.0))));
            });


        String headingJer = """
            {
              "messageId": 31,
              "value": {
                "TravelerInformation": {
                  "msgCnt": 1,
                  "timeStamp": 1,
                  "packetID": "000000000000000000",
                  "dataFrames": [
                    {
                      "doNotUse1": 0,
                      "frameType": "roadSignage",
                      "msgId": {"furtherInfoID": "0000"},
                      "startYear": 2026,
                      "startTime": 1,
                      "durationTime": 60,
                      "priority": 4,
                      "doNotUse2": 0,
                      "regions": [
                        {
                          "anchor": {"lat": 0, "long": 0, "elevation": 0},
                          "description": {
                            "geometry": {
                              "direction": "8000",
                              "circle": {
                                "center": {"lat": 0, "long": 0, "elevation": 0},
                                "radius": 1,
                                "units": "meter"
                              }
                            }
                          }
                        }
                      ],
                      "doNotUse3": 0,
                      "doNotUse4": 0,
                      "content": {
                        "advisory": [
                          {"item": {"itis": 769}},
                          {"item": {"itis": 9478}},
                          {"item": {"itis": 7747}}
                        ]
                      }
                    }
                  ]
                }
              }
            }
            """;

        ValidationResult networkFreeResult = roadBackedService.validateTimJer(
            headingJer,
            ValidationOptions.networkFree());

        assertEquals(0, providerCalls.get(),
            "Disabled roadway heading validation must not call the provider");
        assertFalse(networkFreeResult.isRoadwayHeadingValidationEnabled());
        assertTrue(networkFreeResult.getWarnings().stream().noneMatch(issue ->
            issue.message().contains("not tangent")));

        ValidationResult result = roadBackedService.validateTimJer(
            headingJer,
            ValidationOptions.withRoadwayHeading());

        assertEquals(1, providerCalls.get());
        assertTrue(result.isRoadwayHeadingValidationEnabled());
        assertTrue(result.isValid(), result.getSummary());
        assertTrue(result.getValidationChecks().get("Best Practices").isPassed());
        assertTrue(result.getWarnings().stream().anyMatch(issue ->
            issue.severity() == ValidationSeverity.WARNING
                && issue.checkName().equals("Best Practices")
                && "/value/TravelerInformation/dataFrames/0/regions/0/description/geometry/direction"
                    .equals(issue.path())
                && issue.message().contains("not tangent")));
        assertFalse(result.getWarnings().stream().anyMatch(issue ->
            issue.message().contains("missing offset path description")));
        assertEquals(0, result.getErrors().size());
    }

    @Test
    public void validateTimJer_defaultServiceSupportsEnabledRoadwayHeading() throws Exception {
        ValidationResult result = validationService.validateTimJer(
            "{\"messageId\":31}",
            ValidationOptions.withRoadwayHeading());

        assertTrue(result.isRoadwayHeadingValidationEnabled());
    }

    @Test
    public void validateTimJer_computedLaneReturnsNonBlockingBestPracticesWarning() throws Exception {
        ValidationResult result = validationService.validateTimJer("""
            {
              "messageId": 31,
              "value": {
                "TravelerInformation": {
                  "msgCnt": 1,
                  "timeStamp": 1,
                  "packetID": "000000000000000000",
                  "dataFrames": [
                    {
                      "doNotUse1": 0,
                      "frameType": "roadSignage",
                      "msgId": {"furtherInfoID": "0000"},
                      "startYear": 2026,
                      "startTime": 1,
                      "durationTime": 60,
                      "priority": 4,
                      "doNotUse2": 0,
                      "regions": [
                        {
                          "anchor": {"lat": 0, "long": 0, "elevation": 0},
                          "laneWidth": 300,
                          "directionality": "forward",
                          "closedPath": false,
                          "description": {
                            "path": {
                              "scale": 0,
                              "offset": {
                                "xy": {
                                  "computed": {
                                    "referenceLaneId": 7,
                                    "offsetXaxis": {"small": 0},
                                    "offsetYaxis": {"small": 300}
                                  }
                                }
                              }
                            }
                          }
                        }
                      ],
                      "doNotUse3": 0,
                      "doNotUse4": 0,
                      "content": {
                        "advisory": [
                          {"item": {"itis": 769}},
                          {"item": {"itis": 9478}},
                          {"item": {"itis": 7747}}
                        ]
                      }
                    }
                  ]
                }
              }
            }
            """);

        assertTrue(result.isValid(), result.getSummary());
        assertTrue(result.getValidationChecks().get("Best Practices").isPassed());
        assertTrue(result.getWarnings().stream().anyMatch(issue ->
            issue.severity() == ValidationSeverity.WARNING
                && issue.checkName().equals("Best Practices")
                && ("/value/TravelerInformation/dataFrames/0/regions/0/description/path/offset/xy/"
                    + "computed/referenceLaneId").equals(issue.path())
                && issue.message().contains("left-most lane in the direction of traffic")));
        assertEquals(0, result.getErrors().size());
    }

    @Test
    public void validateTim_itwgValidUperWithIncompleteItisPattern_returnsItisIssues() throws Exception {
        Assumptions.assumeTrue(isNativeLibraryAvailable(),
            "Native codec library not found; skipping end-to-end validation test");

        ValidationResult result = validationService.validateTim(ITWG_VALID_UPER_HEX);

        assertFalse(result.isValid(), "UPER with incomplete ITIS pattern should fail full validation");
        assertNotNull(result.getXerFormat(), "XER output should be captured");
        assertNotNull(result.getTimMessage(), "Deserialized TIM message should be captured");
        assertTrue(result.getValidationChecks().get("J2735 Schema Validation").isPassed(),
            "J2735 schema validation should pass before ITIS validation fails");
        assertTrue(result.getValidationChecks().get("ITWG Schema Validation").isPassed(),
            "ITWG schema validation should pass before ITIS validation fails");
        assertFalse(result.getValidationChecks().get("ITIS Content Validation").isPassed(),
            "ITIS content validation should fail for an incomplete pattern");
        assertTrue(result.getErrors().stream()
            .anyMatch(issue -> issue.checkName().equals("ITIS Content Validation")));
    }

    @Test
    public void validateTim_fullyValidUper_runsEndToEnd() throws Exception {
        Assumptions.assumeTrue(isNativeLibraryAvailable(),
            "Native codec library not found; skipping end-to-end validation test");

        ValidationResult result = validationService.validateTim(FULLY_VALID_UPER_HEX);

        assertTrue(result.isValid(), "Fully valid UPER should pass the full validation pipeline");
        assertTrue(result.getErrors().isEmpty(), "Fully valid UPER should not produce errors");
        assertNotNull(result.getXerFormat(), "XER output should be captured");
        assertNotNull(result.getTimMessage(), "Deserialized TIM message should be captured");
        assertTrue(result.getValidationChecks().get("J2735 Schema Validation").isPassed());
        assertTrue(result.getValidationChecks().get("ITWG Schema Validation").isPassed());
        assertTrue(result.getValidationChecks().get("ITIS Content Validation").isPassed());
        assertTrue(result.getValidationChecks().get("Best Practices").isPassed());
        assertTrue(result.getValidationDurationMs() > 0,
            "Validation duration should record elapsed validation time");
    }

    @Test
    public void validateTim_complexValidUper_runsEndToEnd() throws Exception {
        Assumptions.assumeTrue(isNativeLibraryAvailable(),
            "Native codec library not found; skipping end-to-end validation test");

        ValidationResult result = validationService.validateTim(COMPLEX_VALID_UPER_HEX);

        assertTrue(result.isValid(), "Complex valid UPER should pass the full validation pipeline");
        assertTrue(result.getErrors().isEmpty(), "Complex valid UPER should not produce errors");
        assertNotNull(result.getXerFormat(), "XER output should be captured");
        assertNotNull(result.getTimMessage(), "Deserialized TIM message should be captured");
        assertTrue(result.getValidationChecks().get("J2735 Schema Validation").isPassed());
        assertTrue(result.getValidationChecks().get("ITWG Schema Validation").isPassed());
        assertTrue(result.getValidationChecks().get("ITIS Content Validation").isPassed());
        assertTrue(result.getValidationChecks().get("Best Practices").isPassed());
    }

    @Test
    public void validateTim_legacyUperMissingItwgFields_returnsSchemaIssues() throws Exception {
        Assumptions.assumeTrue(isNativeLibraryAvailable(),
            "Native codec library not found; skipping end-to-end validation test");

        ValidationResult result = validationService.validateTim(LEGACY_UPER_MISSING_ITWG_FIELDS_HEX);

        assertFalse(result.isValid(), "Legacy sample should fail full validation");
        assertTrue(result.getValidationChecks().get("J2735 Schema Validation").isPassed(),
            "Legacy sample should still pass the generated J2735 schema");
        assertFalse(result.getValidationChecks().get("ITWG Schema Validation").isPassed(),
            "Legacy sample should fail the stricter ITWG schema");
        assertTrue(result.getValidationChecks().containsKey("ITIS Content Validation"),
            "ITIS validation should still run after ITWG schema failure");
        assertTrue(result.getValidationChecks().containsKey("Best Practices"),
            "Best Practices should still run after ITWG schema failure");
        assertTrue(result.getErrors().stream()
            .anyMatch(issue -> issue.checkName().equals("ITWG Schema Validation")));
    }

    @Test
    public void validateTim_invalidUperHex_returnsPipelineIssue() {
        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> validationService.validateTim("this-is-not-hex")
        );

        assertNotNull(ex.getValidationResult(), "Validation exception should include the partial result");
        assertFalse(ex.getValidationResult().isValid(), "Invalid hex should fail validation");
        assertTrue(ex.getValidationResult().getValidationDurationMs() > 0,
            "Validation duration should be recorded for failed validations");
        assertTrue(ex.getValidationResult().getErrors().stream()
            .anyMatch(issue -> issue.checkName().equals("Validation Pipeline")));
    }

    private static boolean isNativeLibraryAvailable() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String fileName = osName.contains("win") ? "asnapplication.dll" : "libasnapplication.so";

        Path[] candidates = new Path[] {
            Paths.get(fileName),
            Paths.get("target", "libs", fileName),
            Paths.get("libs", fileName)
        };

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return true;
            }
        }

        return false;
    }
}
