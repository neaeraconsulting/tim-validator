package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.CIRCLE_PATH;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.DESCRIPTION_PATH;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.FRAME_PATH;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.REGION_PATH;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.TRAVELER_INFORMATION_PATH;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.XY_FIRST_NODE_PATH;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.XY_NODES_PATH;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.circleTimJson;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.computedLaneTimJson;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.openPathTimJson;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.pathTimWithNodeAttributes;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.remove;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.resizeArray;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.set;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.NodeEncoding;

/**
 * Provides checklist-oriented regression coverage for the individual rules in
 * the ITWG TIM JSON schema. Structural error-shape tests remain in
 * {@link ItwgTimJsonValidatorTest}.
 */
class ItwgTimSchemaConstraintTest {

    private static final ItwgTimJsonValidator VALIDATOR = new ItwgTimJsonValidator();
    private static final String GEOMETRY_PATH = DESCRIPTION_PATH + "/geometry";
    private static final String CENTER_PATH = CIRCLE_PATH + "/center";
    private static final String COMPUTED_PATH = DESCRIPTION_PATH + "/path/offset/xy/computed";

    /** Verifies each checklist field represented by a schema required list. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("requiredFieldCases")
    void validateJson_requiredField_isEnforced(InvalidCase testCase) {
        assertInvalid(testCase);
    }

    /** Verifies that every numeric minimum and maximum accepts its endpoint. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("validNumericBoundaryCases")
    void validateJson_numericBoundary_acceptsEndpoint(ValidCase testCase) {
        assertDoesNotThrow(() -> VALIDATOR.validateJson(testCase.json()));
    }

    /** Verifies that values immediately outside each numeric range are rejected. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidNumericBoundaryCases")
    void validateJson_numericBoundary_rejectsOutsideValue(InvalidCase testCase) {
        assertInvalid(testCase);
    }

    /** Verifies that schema-sized arrays accept their minimum and maximum lengths. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("validArrayBoundaryCases")
    void validateJson_arrayBoundary_acceptsEndpoint(ValidCase testCase) {
        assertDoesNotThrow(() -> VALIDATOR.validateJson(testCase.json()));
    }

    /** Verifies that arrays immediately outside their allowed lengths are rejected. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidArrayBoundaryCases")
    void validateJson_arrayBoundary_rejectsOutsideSize(InvalidCase testCase) {
        assertInvalid(testCase);
    }

    /** Verifies constants, enumerations, patterns, and special nonzero values. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("fixedValueCases")
    void validateJson_fixedValueRule_isEnforced(InvalidCase testCase) {
        assertInvalid(testCase);
    }

    /** Verifies profile-specific fields rejected by additionalProperties. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("forbiddenPropertyCases")
    void validateJson_forbiddenProperty_isRejected(InvalidCase testCase) {
        assertInvalid(testCase);
    }

    /** Builds missing-field cases from valid fixtures and their owning objects. */
    private static Stream<InvalidCase> requiredFieldCases() {
        String circle = circleTimJson();
        String openPath = openPathTimJson();
        String closedPath = ItwgTimTestFixtures.closedPathTimJson();
        String computed = computedLaneTimJson();
        String msgIdPath = FRAME_PATH + "/msgId";
        String contentPath = FRAME_PATH + "/content";
        String anchorPath = REGION_PATH + "/anchor";
        String pathPath = DESCRIPTION_PATH + "/path";
        String offsetPath = pathPath + "/offset";

        return Stream.of(
            required("messageId", circle, "/messageId", ""),
            required("value", circle, "/value", ""),
            required("TravelerInformation", circle, "/value/TravelerInformation", "/value"),
            required("msgCnt", circle, TRAVELER_INFORMATION_PATH + "/msgCnt", TRAVELER_INFORMATION_PATH),
            required("timeStamp", circle, TRAVELER_INFORMATION_PATH + "/timeStamp", TRAVELER_INFORMATION_PATH),
            required("packetID", circle, TRAVELER_INFORMATION_PATH + "/packetID", TRAVELER_INFORMATION_PATH),
            required("dataFrames", circle, TRAVELER_INFORMATION_PATH + "/dataFrames", TRAVELER_INFORMATION_PATH),
            required("doNotUse1", circle, FRAME_PATH + "/doNotUse1", FRAME_PATH),
            required("frameType", circle, FRAME_PATH + "/frameType", FRAME_PATH),
            required("msgId", circle, FRAME_PATH + "/msgId", FRAME_PATH),
            required("furtherInfoID", circle, msgIdPath + "/furtherInfoID", msgIdPath),
            required("startYear", circle, FRAME_PATH + "/startYear", FRAME_PATH),
            required("startTime", circle, FRAME_PATH + "/startTime", FRAME_PATH),
            required("durationTime", circle, FRAME_PATH + "/durationTime", FRAME_PATH),
            required("priority", circle, FRAME_PATH + "/priority", FRAME_PATH),
            required("doNotUse2", circle, FRAME_PATH + "/doNotUse2", FRAME_PATH),
            required("regions", circle, FRAME_PATH + "/regions", FRAME_PATH),
            required("doNotUse3", circle, FRAME_PATH + "/doNotUse3", FRAME_PATH),
            required("doNotUse4", circle, FRAME_PATH + "/doNotUse4", FRAME_PATH),
            required("content", circle, FRAME_PATH + "/content", FRAME_PATH),
            required("advisory", circle, contentPath + "/advisory", contentPath),
            required("region description", circle, DESCRIPTION_PATH, REGION_PATH),
            required("open-path anchor", openPath, REGION_PATH + "/anchor", REGION_PATH),
            required("open-path closedPath", openPath, REGION_PATH + "/closedPath", REGION_PATH),
            required("open-path laneWidth", openPath, REGION_PATH + "/laneWidth", REGION_PATH),
            required("open-path directionality", openPath, REGION_PATH + "/directionality", REGION_PATH),
            required("closed-path direction", closedPath, REGION_PATH + "/direction", REGION_PATH),
            required("anchor latitude", openPath, anchorPath + "/lat", anchorPath),
            required("anchor longitude", openPath, anchorPath + "/long", anchorPath),
            required("anchor elevation", openPath, anchorPath + "/elevation", anchorPath),
            required("path offset", openPath, offsetPath, pathPath),
            required("node delta", openPath, XY_FIRST_NODE_PATH + "/delta", XY_FIRST_NODE_PATH),
            required("geometry direction", circle, GEOMETRY_PATH + "/direction", GEOMETRY_PATH),
            required("geometry circle", circle, CIRCLE_PATH, GEOMETRY_PATH),
            required("circle center", circle, CENTER_PATH, CIRCLE_PATH),
            required("circle radius", circle, CIRCLE_PATH + "/radius", CIRCLE_PATH),
            required("circle units", circle, CIRCLE_PATH + "/units", CIRCLE_PATH),
            required("center latitude", circle, CENTER_PATH + "/lat", CENTER_PATH),
            required("center longitude", circle, CENTER_PATH + "/long", CENTER_PATH),
            required("center elevation", circle, CENTER_PATH + "/elevation", CENTER_PATH),
            required("computed referenceLaneId", computed, COMPUTED_PATH + "/referenceLaneId", COMPUTED_PATH),
            required("computed offsetXaxis", computed, COMPUTED_PATH + "/offsetXaxis", COMPUTED_PATH),
            required("computed offsetYaxis", computed, COMPUTED_PATH + "/offsetYaxis", COMPUTED_PATH)
        );
    }

    /** Expands all numeric specifications into valid endpoint cases. */
    private static Stream<ValidCase> validNumericBoundaryCases() {
        return numericFields().flatMap(NumericField::validCases);
    }

    /** Expands all numeric specifications into invalid outside-range cases. */
    private static Stream<InvalidCase> invalidNumericBoundaryCases() {
        return numericFields().flatMap(NumericField::invalidCases);
    }

    /** Lists numeric fields whose ranges are part of the schema checklist. */
    private static Stream<NumericField> numericFields() {
        String circle = circleTimJson();
        String openPath = openPathTimJson();
        String computed = computedLaneTimJson();
        String largeComputed = largeComputedLaneTimJson();
        String attributes = pathTimWithNodeAttributes(
            NodeEncoding.OPEN_XY,
            "{\"dWidth\":5,\"dElevation\":-5}");
        String anchorPath = REGION_PATH + "/anchor";

        return Stream.of(
            numeric("msgCnt", circle, TRAVELER_INFORMATION_PATH + "/msgCnt", 0, 127),
            numeric("timeStamp", circle, TRAVELER_INFORMATION_PATH + "/timeStamp", 0, 527039),
            numeric("startYear", circle, FRAME_PATH + "/startYear", 2000, 4095),
            numeric("startTime", circle, FRAME_PATH + "/startTime", 1, 527039),
            numeric("durationTime", circle, FRAME_PATH + "/durationTime", 1, 32000),
            numeric("priority", circle, FRAME_PATH + "/priority", 0, 7),
            numeric("anchor latitude", openPath, anchorPath + "/lat", -900000000, 900000000),
            numeric("anchor longitude", openPath, anchorPath + "/long", -1799999999, 1800000000),
            numeric("anchor elevation", openPath, anchorPath + "/elevation", -4096, 61439),
            numeric("laneWidth", openPath, REGION_PATH + "/laneWidth", 1, 32767),
            numeric("center latitude", circle, CENTER_PATH + "/lat", -900000000, 900000000),
            numeric("center longitude", circle, CENTER_PATH + "/long", -1799999999, 1800000000),
            numeric("center elevation", circle, CENTER_PATH + "/elevation", -4096, 61439),
            numeric("circle radius", circle, CIRCLE_PATH + "/radius", 1, 4094),
            numeric("XY delta x", openPath, XY_FIRST_NODE_PATH + "/delta/node-XY1/x", -512, 511),
            numeric("dWidth", attributes, XY_FIRST_NODE_PATH + "/attributes/dWidth", -512, 511),
            numeric("dElevation", attributes, XY_FIRST_NODE_PATH + "/attributes/dElevation", -512, 511),
            numeric("referenceLaneId", computed, COMPUTED_PATH + "/referenceLaneId", 0, 254),
            numeric("small offsetXAxis", computed, COMPUTED_PATH + "/offsetXaxis/small", -2047, 2047),
            numeric("small offsetYAxis", computed, COMPUTED_PATH + "/offsetYaxis/small", -2047, 2047),
            numeric("large offsetXAxis", largeComputed, COMPUTED_PATH + "/offsetXaxis/large", -32767, 32767),
            numeric("large offsetYAxis", largeComputed, COMPUTED_PATH + "/offsetYaxis/large", -32767, 32767),
            numeric("rotateXY", computedWithRotateJson(), COMPUTED_PATH + "/rotateXY", 0, 28800)
        );
    }

    /** Expands all array specifications into valid endpoint cases. */
    private static Stream<ValidCase> validArrayBoundaryCases() {
        return arrayFields().flatMap(ArrayField::validCases);
    }

    /** Expands all array specifications into invalid outside-range cases. */
    private static Stream<InvalidCase> invalidArrayBoundaryCases() {
        return arrayFields().flatMap(ArrayField::invalidCases);
    }

    /** Lists arrays whose cardinalities are explicitly constrained by the profile. */
    private static Stream<ArrayField> arrayFields() {
        String circle = circleTimJson();
        String openPath = openPathTimJson();
        return Stream.of(
            array("dataFrames", circle, TRAVELER_INFORMATION_PATH + "/dataFrames", 1, 8),
            array("regions", circle, FRAME_PATH + "/regions", 1, 16),
            array("advisory items", circle, FRAME_PATH + "/content/advisory", 1, 100),
            array("open-path nodes", openPath, XY_NODES_PATH, 2, 63)
        );
    }

    /** Builds invalid cases for constants, enumerations, patterns, and nonzero deltas. */
    private static Stream<InvalidCase> fixedValueCases() {
        String circle = circleTimJson();
        String openPath = openPathTimJson();
        return Stream.of(
            invalidValue("messageId", circle, "/messageId", "30"),
            invalidValue("packetID format", circle, TRAVELER_INFORMATION_PATH + "/packetID", "\"123\""),
            invalidValue("doNotUse1", circle, FRAME_PATH + "/doNotUse1", "1"),
            invalidValue("frameType", circle, FRAME_PATH + "/frameType", "\"unknown\""),
            invalidValue("furtherInfoID", circle, FRAME_PATH + "/msgId/furtherInfoID", "\"0001\""),
            invalidValue("doNotUse2", circle, FRAME_PATH + "/doNotUse2", "1"),
            invalidValue("doNotUse3", circle, FRAME_PATH + "/doNotUse3", "1"),
            invalidValue("doNotUse4", circle, FRAME_PATH + "/doNotUse4", "1"),
            invalidValue("directionality", openPath, REGION_PATH + "/directionality", "\"sideways\""),
            invalidValue("closedPath type", openPath, REGION_PATH + "/closedPath", "\"false\""),
            invalidValue("circle units", circle, CIRCLE_PATH + "/units", "\"unknown\""),
            invalidValue(
                "zero dWidth",
                pathTimWithNodeAttributes(NodeEncoding.OPEN_XY, "{\"dWidth\":5}"),
                XY_FIRST_NODE_PATH + "/attributes/dWidth",
                "0"),
            invalidValue(
                "zero dElevation",
                pathTimWithNodeAttributes(NodeEncoding.OPEN_XY, "{\"dElevation\":5}"),
                XY_FIRST_NODE_PATH + "/attributes/dElevation",
                "0")
        );
    }

    /** Builds cases for every profile field explicitly excluded by the checklist. */
    private static Stream<InvalidCase> forbiddenPropertyCases() {
        String circle = circleTimJson();
        String computed = computedLaneTimJson();
        return Stream.of(
            forbidden("urlB", circle, FRAME_PATH + "/urlB", "\"https://example.com\"", FRAME_PATH),
            forbidden("url", circle, FRAME_PATH + "/url", "\"https://example.com\"", FRAME_PATH),
            forbidden("TIM regional", circle, TRAVELER_INFORMATION_PATH + "/regional", "{}", TRAVELER_INFORMATION_PATH),
            forbidden("roadSignID", circle, FRAME_PATH + "/msgId/roadSignID", "{}", FRAME_PATH + "/msgId"),
            forbidden("region name", circle, REGION_PATH + "/name", "\"road\"", REGION_PATH),
            forbidden("region id", circle, REGION_PATH + "/id", "{}", REGION_PATH),
            forbidden("region regional", circle, REGION_PATH + "/regional", "{}", REGION_PATH),
            forbidden("oldRegion", circle, DESCRIPTION_PATH + "/oldRegion", "{}", DESCRIPTION_PATH),
            forbidden("workZone content", circle, FRAME_PATH + "/content/workZone", "{}", FRAME_PATH + "/content"),
            forbidden(
                "genericSign content",
                circle,
                FRAME_PATH + "/content/genericSign",
                "{}",
                FRAME_PATH + "/content"),
            forbidden("speedLimit content", circle, FRAME_PATH + "/content/speedLimit", "{}", FRAME_PATH + "/content"),
            forbidden(
                "exitService content",
                circle,
                FRAME_PATH + "/content/exitService",
                "{}",
                FRAME_PATH + "/content"),
            forbidden(
                "URL content",
                circle,
                FRAME_PATH + "/content/url",
                "\"https://example.com\"",
                FRAME_PATH + "/content"),
            forbidden("geometry extent", circle, GEOMETRY_PATH + "/extent", "\"useFor100meters\"", GEOMETRY_PATH),
            forbidden("geometry regional", circle, GEOMETRY_PATH + "/regional", "{}", GEOMETRY_PATH),
            forbidden("circle regional", circle, CIRCLE_PATH + "/regional", "{}", CIRCLE_PATH),
            forbidden("scaleXAxis", computed, COMPUTED_PATH + "/scaleXAxis", "1", COMPUTED_PATH),
            forbidden("scaleYAxis", computed, COMPUTED_PATH + "/scaleYAxis", "1", COMPUTED_PATH),
            forbidden("computed regional", computed, COMPUTED_PATH + "/regional", "{}", COMPUTED_PATH)
        );
    }

    /** Creates an invalid case by removing a required field. */
    private static InvalidCase required(
            String name,
            String json,
            String fieldPath,
            String expectedPath) {
        return new InvalidCase(name, remove(json, fieldPath), expectedPath, fieldName(fieldPath));
    }

    /** Creates a numeric range specification. */
    private static NumericField numeric(
            String name,
            String json,
            String fieldPath,
            long minimum,
            long maximum) {
        return new NumericField(name, json, fieldPath, minimum, maximum);
    }

    /** Creates an array cardinality specification. */
    private static ArrayField array(
            String name,
            String json,
            String fieldPath,
            int minimum,
            int maximum) {
        return new ArrayField(name, json, fieldPath, minimum, maximum);
    }

    /** Creates an invalid case by replacing a constrained scalar value. */
    private static InvalidCase invalidValue(
            String name,
            String json,
            String fieldPath,
            String invalidValueJson) {
        return new InvalidCase(name, set(json, fieldPath, invalidValueJson), fieldPath, null);
    }

    /** Creates an invalid case by adding a forbidden property. */
    private static InvalidCase forbidden(
            String name,
            String json,
            String fieldPath,
            String invalidValueJson,
            String expectedPath) {
        return new InvalidCase(
            name,
            set(json, fieldPath, invalidValueJson),
            expectedPath,
            fieldName(fieldPath));
    }

    /** Returns a valid computed lane that uses the large axis alternatives. */
    private static String largeComputedLaneTimJson() {
        String json = set(
            computedLaneTimJson(),
            COMPUTED_PATH + "/offsetXaxis",
            "{\"large\":0}");
        return set(json, COMPUTED_PATH + "/offsetYaxis", "{\"large\":0}");
    }

    /** Returns a valid computed lane containing its optional rotateXY field. */
    private static String computedWithRotateJson() {
        return set(computedLaneTimJson(), COMPUTED_PATH + "/rotateXY", "0");
    }

    /** Returns the final field token of the test suite's simple JSON Pointers. */
    private static String fieldName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** Asserts that a case produces an ITWG issue at the expected location. */
    private static void assertInvalid(InvalidCase testCase) {
        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> VALIDATOR.validateJson(testCase.json()));

        assertTrue(ex.getIssues().stream().anyMatch(issue ->
            issue.checkName().equals("ITWG Schema Validation")
                && issue.path().equals(testCase.expectedPath())
                && (testCase.messageText() == null
                    || issue.message().contains(testCase.messageText()))),
            () -> ex.getIssues().toString());
    }

    /** Describes one expected schema failure. */
    private record InvalidCase(
            String name,
            String json,
            String expectedPath,
            String messageText) {

        /** Uses the concise scenario name in parameterized test output. */
        @Override
        public String toString() {
            return name;
        }
    }

    /** Describes one schema-valid boundary document. */
    private record ValidCase(String name, String json) {

        /** Uses the concise scenario name in parameterized test output. */
        @Override
        public String toString() {
            return name;
        }
    }

    /** Generates endpoint and outside-range cases for one numeric field. */
    private record NumericField(
            String name,
            String json,
            String fieldPath,
            long minimum,
            long maximum) {

        /** Returns valid cases at the inclusive minimum and maximum. */
        Stream<ValidCase> validCases() {
            return Stream.of(
                new ValidCase(name + " minimum", set(json, fieldPath, Long.toString(minimum))),
                new ValidCase(name + " maximum", set(json, fieldPath, Long.toString(maximum))));
        }

        /** Returns invalid cases immediately below and above the allowed range. */
        Stream<InvalidCase> invalidCases() {
            return Stream.of(
                new InvalidCase(
                    name + " below minimum",
                    set(json, fieldPath, Long.toString(minimum - 1)),
                    fieldPath,
                    null),
                new InvalidCase(
                    name + " above maximum",
                    set(json, fieldPath, Long.toString(maximum + 1)),
                    fieldPath,
                    null));
        }
    }

    /** Generates endpoint and outside-range cases for one array field. */
    private record ArrayField(
            String name,
            String json,
            String fieldPath,
            int minimum,
            int maximum) {

        /** Returns valid cases at the inclusive minimum and maximum lengths. */
        Stream<ValidCase> validCases() {
            return Stream.of(
                new ValidCase(name + " minimum", resizeArray(json, fieldPath, minimum)),
                new ValidCase(name + " maximum", resizeArray(json, fieldPath, maximum)));
        }

        /** Returns invalid cases immediately below and above the allowed lengths. */
        Stream<InvalidCase> invalidCases() {
            return Stream.of(
                new InvalidCase(
                    name + " below minimum",
                    resizeArray(json, fieldPath, minimum - 1),
                    fieldPath,
                    null),
                new InvalidCase(
                    name + " above maximum",
                    resizeArray(json, fieldPath, maximum + 1),
                    fieldPath,
                    null));
        }
    }
}
