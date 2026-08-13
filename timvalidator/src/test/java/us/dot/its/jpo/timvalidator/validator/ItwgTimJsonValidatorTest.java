package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.CIRCLE_PATH;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.DESCRIPTION_PATH;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.FRAME_PATH;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.REGION_PATH;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.XY_FIRST_NODE_PATH;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.circleTimJson;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.circleTimWithContentNewJson;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.closedPathTimJson;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.closedPathTimWithoutDirectionJson;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.computedLaneTimJson;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.descriptionChoiceConflictTimJson;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.pathTimWithNodeAttributes;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.set;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.threeNodeClosedPathTimJson;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import us.dot.its.jpo.timvalidator.converter.JerToMessageFrameConverter;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.NodeEncoding;

class ItwgTimJsonValidatorTest {

    private static final ItwgTimJsonValidator VALIDATOR = new ItwgTimJsonValidator();
    private static final JerToMessageFrameConverter CONVERTER = new JerToMessageFrameConverter();

    /** Verifies that contentNew remains optional on the deserialized validation path. */
    @Test
    void validate_missingContentNew_passesValidation() {
        var messageFrame = CONVERTER.deserialize(circleTimJson());

        assertDoesNotThrow(() -> VALIDATOR.validate(messageFrame));
    }

    /** Verifies that contentNew remains optional for raw JER input. */
    @Test
    void validateJson_missingContentNew_passesValidation() {
        assertDoesNotThrow(() -> VALIDATOR.validateJson(circleTimJson()));
    }

    /** Verifies each reusable base fixture before it is used by mutation tests. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("validTimCases")
    void validateJson_validItwgTim_passesValidation(String scenario, String json) {
        assertDoesNotThrow(() -> VALIDATOR.validateJson(json));
    }

    /**
     * Verifies the node-attribute rule through both raw JSON and deserialized
     * MessageFrame validation paths.
     */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("nodeAttributeCases")
    void validate_nodeAttributesRequireMeaningfulValues(
            NodeEncoding encoding,
            String scenario,
            String attributesJson,
            String expectedErrorSuffix) {
        String json = pathTimWithNodeAttributes(encoding, attributesJson);
        String expectedErrorPath = expectedErrorSuffix == null
            ? null
            : encoding.firstNodePath() + "/attributes" + expectedErrorSuffix;

        assertAttributeValidation(expectedErrorPath, () -> VALIDATOR.validateJson(json));

        var messageFrame = CONVERTER.deserialize(json);
        assertAttributeValidation(expectedErrorPath, () -> VALIDATOR.validate(messageFrame));
    }

    /** Verifies precise paths for invalid values beneath normalized schema choices. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("preciseLeafErrorCases")
    void validateJson_invalidChoiceValueReportsOnlyExactLeaf(
            String scenario,
            String json,
            String expectedPath) {
        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> VALIDATOR.validateJson(json));

        assertSingleIssueAt(ex, expectedPath);
    }

    /** Verifies precise paths for fields forbidden by a selected region type. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("preciseRegionErrorCases")
    void validateJson_invalidRegionFieldReportsOnlyExactField(
            String scenario,
            String json,
            String expectedPath) {
        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> VALIDATOR.validateJson(json));

        assertSingleIssueAt(ex, expectedPath);
    }

    /** Verifies precise object paths when mutually exclusive choices are combined. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("preciseChoiceCardinalityCases")
    void validateJson_multipleChoicePropertiesReportOnlyChoiceObject(
            String scenario,
            String json,
            String expectedPath) {
        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> VALIDATOR.validateJson(json));

        assertSingleIssueAt(ex, expectedPath);
    }

    /** Verifies the containing path and field name for forbidden profile properties. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("forbiddenProfileFieldCases")
    void validateJson_forbiddenProfileField_reportsContainingObjectAndField(
            String scenario,
            String json,
            String expectedPath,
            String expectedField) {
        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> VALIDATOR.validateJson(json));

        assertSingleIssueAtContaining(ex, expectedPath, expectedField);
    }

    /** Verifies that a closed path reports its missing direction at the region. */
    @Test
    void validateJson_closedPathRegionWithoutDirection_reportsRegionIssue() {
        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> VALIDATOR.validateJson(closedPathTimWithoutDirectionJson()));

        assertIssueAtContaining(ex, REGION_PATH, "direction");
    }

    /** Verifies that the closed-path polygon minimum is reported at the node array. */
    @Test
    void validateJson_closedPathRegionWithThreeNodes_reportsNodeArrayIssue() {
        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> VALIDATOR.validateJson(threeNodeClosedPathTimJson()));

        assertSingleIssueAt(ex, DESCRIPTION_PATH + "/path/offset/xy/nodes");
    }

    /** Verifies that circle regions are not required to contain an anchor. */
    @Test
    void validateJson_circleRegionWithoutAnchor_passesValidation() {
        String json = ItwgTimTestFixtures.remove(
            circleTimJson(),
            REGION_PATH + "/anchor");

        assertDoesNotThrow(() -> VALIDATOR.validateJson(json));
    }

    /** Supplies each schema-valid base representation used by the test suite. */
    private static Stream<Arguments> validTimCases() {
        return Stream.of(
            Arguments.of("circle", circleTimJson()),
            Arguments.of("open XY path", pathTimWithNodeAttributes(NodeEncoding.OPEN_XY, null)),
            Arguments.of("open LL path", pathTimWithNodeAttributes(NodeEncoding.OPEN_LL, null)),
            Arguments.of("closed XY path", closedPathTimJson()),
            Arguments.of("closed LL path", pathTimWithNodeAttributes(NodeEncoding.CLOSED_LL, null)),
            Arguments.of("computed lane", computedLaneTimJson())
        );
    }

    /** Supplies valid and invalid attribute cases for every supported node representation. */
    private static Stream<Arguments> nodeAttributeCases() {
        return Arrays.stream(NodeEncoding.values()).flatMap(encoding -> Stream.of(
            Arguments.of(encoding, "attributes absent", null, null),
            Arguments.of(encoding, "empty attributes", "{}", ""),
            Arguments.of(
                encoding,
                "nonzero width and elevation",
                "{\"dWidth\": 5, \"dElevation\": -5}",
                null),
            Arguments.of(encoding, "zero width", "{\"dWidth\": 0}", "/dWidth"),
            Arguments.of(encoding, "zero elevation", "{\"dElevation\": 0}", "/dElevation"),
            Arguments.of(
                encoding,
                "zero width with nonzero elevation",
                "{\"dWidth\": 0, \"dElevation\": 5}",
                "/dWidth"),
            Arguments.of(
                encoding,
                "nonzero width with zero elevation",
                "{\"dWidth\": 5, \"dElevation\": 0}",
                "/dElevation"),
            Arguments.of(
                encoding,
                "other meaningful attribute",
                "{\"localNode\": [\"stopLine\"]}",
                null)
        ));
    }

    /** Supplies invalid scalar values beneath each normalized schema-choice family. */
    private static Stream<Arguments> preciseLeafErrorCases() {
        String llFirstNodePath = DESCRIPTION_PATH + "/path/offset/ll/nodes/0";
        return Stream.of(
            invalidFieldCase(
                "geometry child",
                circleTimJson(),
                CIRCLE_PATH + "/radius",
                "0"),
            invalidFieldCase(
                "string-or-object direction child",
                circleTimJson(),
                DESCRIPTION_PATH + "/geometry/direction",
                "\"000\""),
            invalidFieldCase(
                "object direction child",
                circleTimJson(),
                DESCRIPTION_PATH + "/geometry/direction",
                "{\"value\":\"000\",\"length\":16}",
                DESCRIPTION_PATH + "/geometry/direction/value"),
            invalidFieldCase(
                "XY node delta child",
                closedPathTimJson(),
                XY_FIRST_NODE_PATH + "/delta/node-XY1/x",
                "512"),
            invalidFieldCase(
                "LL node delta child",
                pathTimWithNodeAttributes(NodeEncoding.CLOSED_LL, null),
                llFirstNodePath + "/delta/node-LL1/lon",
                "2048"),
            invalidFieldCase(
                "lane data attribute child",
                closedPathTimJson(),
                XY_FIRST_NODE_PATH + "/attributes",
                "{\"data\":[{\"laneAngle\":181}]}",
                XY_FIRST_NODE_PATH + "/attributes/data/0/laneAngle"),
            invalidFieldCase(
                "computed-lane axis child",
                computedLaneTimJson(),
                DESCRIPTION_PATH + "/path/offset/xy/computed/offsetXaxis/small",
                "2048"),
            invalidFieldCase(
                "advisory item child",
                circleTimJson(),
                FRAME_PATH + "/content/advisory/0/item/itis",
                "65536"),
            invalidFieldCase(
                "road-surface child",
                circleTimWithContentNewJson(),
                FRAME_PATH + "/contentNew/frictionInfo/roadSurfaceDescription/asphaltOrTar/type",
                "\"invalid\"")
        );
    }

    /** Supplies conflicts where a JSON object contains two mutually exclusive choices. */
    private static Stream<Arguments> preciseChoiceCardinalityCases() {
        String deltaPath = XY_FIRST_NODE_PATH + "/delta";
        String itemPath = FRAME_PATH + "/content/advisory/0/item";
        return Stream.of(
            Arguments.of(
                "multiple region description representations",
                descriptionChoiceConflictTimJson(),
                DESCRIPTION_PATH),
            invalidFieldCase(
                "multiple node delta representations",
                closedPathTimJson(),
                deltaPath + "/node-XY2",
                "{\"x\":0,\"y\":0}",
                deltaPath),
            invalidFieldCase(
                "multiple advisory item representations",
                circleTimJson(),
                itemPath + "/text",
                "\"extra\"",
                itemPath)
        );
    }

    /** Supplies one forbidden field for every conditional region shape. */
    private static Stream<Arguments> preciseRegionErrorCases() {
        return Stream.of(
            invalidFieldCase(
                "circle with closedPath",
                circleTimJson(),
                REGION_PATH + "/closedPath",
                "false"),
            invalidFieldCase(
                "circle with laneWidth",
                circleTimJson(),
                REGION_PATH + "/laneWidth",
                "1"),
            invalidFieldCase(
                "circle with directionality",
                circleTimJson(),
                REGION_PATH + "/directionality",
                "\"forward\""),
            invalidFieldCase(
                "circle with region direction",
                circleTimJson(),
                REGION_PATH + "/direction",
                "\"0000\""),
            invalidFieldCase(
                "open path with direction",
                pathTimWithNodeAttributes(NodeEncoding.OPEN_XY, null),
                REGION_PATH + "/direction",
                "\"0000\""),
            invalidFieldCase(
                "closed path with laneWidth",
                closedPathTimJson(),
                REGION_PATH + "/laneWidth",
                "1"),
            invalidFieldCase(
                "closed path with directionality",
                closedPathTimJson(),
                REGION_PATH + "/directionality",
                "\"forward\"")
        );
    }

    /** Supplies representative profile fields rejected through object shape restrictions. */
    private static Stream<Arguments> forbiddenProfileFieldCases() {
        return Stream.of(
            forbiddenFieldCase(
                "urlB",
                circleTimJson(),
                FRAME_PATH + "/urlB",
                "\"https://example.com\"",
                FRAME_PATH,
                "urlB"),
            forbiddenFieldCase(
                "roadSignID",
                circleTimJson(),
                FRAME_PATH + "/msgId/roadSignID",
                "{}",
                FRAME_PATH + "/msgId",
                "roadSignID"),
            forbiddenFieldCase(
                "workZone content",
                circleTimJson(),
                FRAME_PATH + "/content/workZone",
                "{}",
                FRAME_PATH + "/content",
                "workZone"),
            forbiddenFieldCase(
                "geometry extent",
                circleTimJson(),
                DESCRIPTION_PATH + "/geometry/extent",
                "\"useFor100meters\"",
                DESCRIPTION_PATH + "/geometry",
                "extent")
        );
    }

    /** Creates a forbidden-property case with its containing path and field name. */
    private static Arguments forbiddenFieldCase(
            String scenario,
            String json,
            String fieldPath,
            String invalidValueJson,
            String expectedPath,
            String expectedField) {
        return Arguments.of(
            scenario,
            set(json, fieldPath, invalidValueJson),
            expectedPath,
            expectedField);
    }

    /** Creates a case whose mutation and expected issue use the same JSON Pointer. */
    private static Arguments invalidFieldCase(
            String scenario,
            String json,
            String fieldPath,
            String invalidValueJson) {
        return invalidFieldCase(scenario, json, fieldPath, invalidValueJson, fieldPath);
    }

    /** Creates a case with separate mutation and expected issue JSON Pointers. */
    private static Arguments invalidFieldCase(
            String scenario,
            String json,
            String fieldPath,
            String invalidValueJson,
            String expectedPath) {
        return Arguments.of(
            scenario,
            set(json, fieldPath, invalidValueJson),
            expectedPath);
    }

    /** Asserts success or a single precise ITWG issue for an attribute case. */
    private static void assertAttributeValidation(
            String expectedErrorPath,
            Executable validation) {
        if (expectedErrorPath == null) {
            assertDoesNotThrow(validation);
            return;
        }

        ValidationException ex = assertThrows(ValidationException.class, validation);
        assertSingleIssueAt(ex, expectedErrorPath);
    }

    /** Asserts that schema validation returned exactly one issue at a JSON Pointer. */
    private static void assertSingleIssueAt(
            ValidationException exception,
            String expectedPath) {
        assertEquals(1, exception.getIssues().size(), () -> exception.getIssues().toString());
        assertEquals("ITWG Schema Validation", exception.getIssues().getFirst().checkName());
        assertEquals(expectedPath, exception.getIssues().getFirst().path());
    }

    /** Asserts one issue at a path whose message identifies the failing field. */
    private static void assertSingleIssueAtContaining(
            ValidationException exception,
            String expectedPath,
            String expectedMessageText) {
        assertSingleIssueAt(exception, expectedPath);
        assertTrue(
            exception.getIssues().getFirst().message().contains(expectedMessageText),
            () -> exception.getIssues().toString());
    }

    /** Asserts that an issue at a JSON Pointer mentions the missing or invalid field. */
    private static void assertIssueAtContaining(
            ValidationException exception,
            String expectedPath,
            String expectedMessageText) {
        assertTrue(exception.getIssues().stream().anyMatch(issue ->
            issue.checkName().equals("ITWG Schema Validation")
                && issue.path().equals(expectedPath)
                && issue.message().contains(expectedMessageText)),
            () -> exception.getIssues().toString());
    }
}
