package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import us.dot.its.jpo.timvalidator.converter.JerToMessageFrameConverter;
import us.dot.its.jpo.timvalidator.exception.ValidationException;

class ItwgTimJsonValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void validate_missingContentNew_passesValidation() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();
        var messageFrame = new JerToMessageFrameConverter().deserialize(validItwgTimJsonWithoutContentNew());

        assertDoesNotThrow(() -> itwgValidator.validate(messageFrame));
    }

    @Test
    void validateJson_missingContentNew_passesValidation() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();

        assertDoesNotThrow(() -> itwgValidator.validateJson(validItwgTimJsonWithoutContentNew()));
    }

    @Test
    void validateJson_validItwgTim_passesValidation() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();

        assertDoesNotThrow(() -> itwgValidator.validateJson(validItwgTimJson()));
    }

    /**
     * Verifies the node-attribute rule through both raw JSON and the
     * deserialized MessageFrame validation path.
     */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("nodeAttributeCases")
    void validate_nodeAttributesRequireMeaningfulValues(
            NodeEncoding encoding,
            String scenario,
            String attributesJson,
            String expectedErrorSuffix) throws Exception {
        String json = itwgTimJsonWithNodeAttributes(encoding, attributesJson);
        ItwgTimJsonValidator validator = new ItwgTimJsonValidator();
        String expectedErrorPath = expectedErrorSuffix == null
            ? null
            : encoding.firstNodePath() + "/attributes" + expectedErrorSuffix;

        assertAttributeValidation(
                expectedErrorPath,
                () -> validator.validateJson(json));

        var messageFrame = new JerToMessageFrameConverter().deserialize(json);
        assertAttributeValidation(
                expectedErrorPath,
                () -> validator.validate(messageFrame));
    }

    /**
     * Verifies that invalid values nested beneath each former schema choice
     * produce one issue at the actual failing value.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("preciseLeafErrorCases")
    void validateJson_invalidChoiceValueReportsOnlyExactLeaf(
            String scenario,
            String json,
            String expectedPath) {
        ItwgTimJsonValidator validator = new ItwgTimJsonValidator();

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> validator.validateJson(json));

        assertSingleIssueAt(ex, expectedPath);
    }

    /**
     * Verifies that fields forbidden by the selected region kind are reported
     * at the forbidden field instead of through unrelated region branches.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("preciseRegionErrorCases")
    void validateJson_invalidRegionFieldReportsOnlyExactField(
            String scenario,
            String json,
            String expectedPath) {
        ItwgTimJsonValidator validator = new ItwgTimJsonValidator();

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> validator.validateJson(json));

        assertSingleIssueAt(ex, expectedPath);
    }

    /**
     * Verifies that objects containing multiple mutually exclusive choice
     * properties report one issue at the choice object.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("preciseChoiceCardinalityCases")
    void validateJson_multipleChoicePropertiesReportOnlyChoiceObject(
            String scenario,
            String json,
            String expectedPath) {
        ItwgTimJsonValidator validator = new ItwgTimJsonValidator();

        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> validator.validateJson(json));

        assertSingleIssueAt(ex, expectedPath);
    }

    @Test
    void validateJson_closedPathRegionWithoutDirection_throwsValidationException() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();

        ValidationException ex = assertThrows(ValidationException.class,
            () -> itwgValidator.validateJson(itwgTimJsonWithClosedPathWithoutDirection()));

        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.message().contains("direction")));
    }

    @Test
    void validateJson_closedPathRegionWithDirection_passesValidation() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();

        assertDoesNotThrow(() -> itwgValidator.validateJson(validItwgTimJsonWithClosedPath()));
    }

    @Test
    void validateJson_closedPathRegionWithThreeNodes_throwsValidationException() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();

        ValidationException ex = assertThrows(ValidationException.class,
            () -> itwgValidator.validateJson(itwgTimJsonWithThreePointClosedPath()));

        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.message().contains("4")));
    }

    @Test
    void validateJson_forbiddenNestedItwgField_throwsValidationException() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();
        String invalidJson = validItwgTimJson().replace(
            "\"doNotUse1\": 0,",
            "\"urlB\": \"https://example.com\",%n                      \"doNotUse1\": 0,".formatted()
        );

        ValidationException ex = assertThrows(ValidationException.class,
            () -> itwgValidator.validateJson(invalidJson));

        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.message().contains("urlB")));
        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.checkName().equals("ITWG Schema Validation")));
    }

    @Test
    void validateJson_forbiddenMsgIdBranchField_reportsFieldSpecificIssue() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();
        String invalidJson = validItwgTimJson().replace(
            """
                      "msgId": {
                        "furtherInfoID": "0000"
                      },
            """,
            """
                      "msgId": {
                        "roadSignID": {
                          "position": {
                            "lat": 0,
                            "long": 0,
                            "elevation": 0
                          },
                          "viewAngle": "0000"
                        }
                      },
            """
        );

        ValidationException ex = assertThrows(ValidationException.class,
            () -> itwgValidator.validateJson(invalidJson));

        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.message().contains("roadSignID")));
        assertTrue(ex.getIssues().stream()
            .noneMatch(issue -> issue.message().contains("one and only one schema")));
    }

    @Test
    void validateJson_forbiddenContentBranchField_reportsFieldSpecificIssue() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();
        String invalidJson = validItwgTimJson().replace(
            """
                      "content": {
                        "advisory": [
                          {
                            "item": {
                              "itis": 268
                            }
                          }
                        ]
                      },
            """,
            """
                      "content": {
                        "workZone": {
                          "item": {
                            "itis": 268
                          }
                        }
                      },
            """
        );

        ValidationException ex = assertThrows(ValidationException.class,
            () -> itwgValidator.validateJson(invalidJson));

        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.message().contains("workZone")));
        assertTrue(ex.getIssues().stream()
            .noneMatch(issue -> issue.message().contains("one and only one schema")));
    }

    @Test
    void validateJson_circleRegionWithoutAnchor_passesValidation() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();
        String circleWithoutAnchor = validItwgTimJson().replace(
            """
                          "anchor": {
                            "lat": 0,
                            "long": 0,
                            "elevation": 0
                          },
            """,
            ""
        );

        assertDoesNotThrow(() -> itwgValidator.validateJson(circleWithoutAnchor));
    }

    @Test
    void validateJson_circleRegionWithClosedPath_throwsValidationException() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();
        String invalidJson = validItwgTimJson().replace(
            """
                          "description": {
            """,
            """
                          "closedPath": false,
                          "description": {
            """
        );

        ValidationException ex = assertThrows(ValidationException.class,
            () -> itwgValidator.validateJson(invalidJson));

        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.message().contains("closedPath")));
    }

    @Test
    void validateJson_circleGeometryWithExtent_throwsValidationException() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();
        String invalidJson = validItwgTimJson().replace(
            """
                              "direction": "0000",
                              "circle": {
            """,
            """
                              "direction": "0000",
                              "extent": "useFor100meters",
                              "circle": {
            """
        );

        ValidationException ex = assertThrows(ValidationException.class,
            () -> itwgValidator.validateJson(invalidJson));

        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.message().contains("extent")));
    }

    @Test
    void validateJson_circleRegionWithPathFields_throwsValidationException() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();
        String invalidJson = validItwgTimJson().replace(
            """
                          "anchor": {
                            "lat": 0,
                            "long": 0,
                            "elevation": 0
                          },
                          "description": {
            """,
            """
                          "anchor": {
                            "lat": 0,
                            "long": 0,
                            "elevation": 0
                          },
                          "laneWidth": 1,
                          "directionality": "forward",
                          "direction": "ffff",
                          "description": {
            """
        );

        ValidationException ex = assertThrows(ValidationException.class,
            () -> itwgValidator.validateJson(invalidJson));

        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.message().contains("laneWidth")));
        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.message().contains("directionality")));
        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.message().contains("direction")));
    }

    @Test
    void validateJson_closedPathRegionWithOpenPathFields_throwsValidationException() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();
        String invalidJson = validItwgTimJsonWithClosedPath().replace(
            "\"direction\": \"0000\",",
            "\"direction\": \"0000\",\n"
                + "                          \"laneWidth\": 1,\n"
                + "                          \"directionality\": \"forward\","
        );

        ValidationException ex = assertThrows(ValidationException.class,
            () -> itwgValidator.validateJson(invalidJson));

        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.message().contains("laneWidth")));
        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.message().contains("directionality")));
    }

    /**
     * Produces the valid and invalid attribute cases for every supported node
     * encoding and path type.
     */
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

    /** Builds invalid scalar cases beneath every normalized schema-choice family. */
    private static Stream<Arguments> preciseLeafErrorCases() throws Exception {
        String regionPath = "/value/TravelerInformation/dataFrames/0/regions/0";
        String xyNodePath = regionPath
            + "/description/path/offset/xy/nodes/0";
        String llNodePath = regionPath
            + "/description/path/offset/ll/nodes/0";

        return Stream.of(
            preciseErrorCase(
                "geometry child",
                validItwgTimJson(),
                regionPath + "/description/geometry/circle/radius",
                "0"),
            preciseErrorCase(
                "string-or-object direction child",
                validItwgTimJson(),
                regionPath + "/description/geometry/direction",
                "\"000\""),
            preciseErrorCase(
                "object direction child",
                validItwgTimJson(),
                regionPath + "/description/geometry/direction",
                "{\"value\":\"000\",\"length\":16}",
                regionPath + "/description/geometry/direction/value"),
            preciseErrorCase(
                "XY node delta child",
                validItwgTimJsonWithClosedPath(),
                xyNodePath + "/delta/node-XY1/x",
                "512"),
            preciseErrorCase(
                "LL node delta child",
                itwgTimJsonWithNodeAttributes(NodeEncoding.CLOSED_LL, null),
                llNodePath + "/delta/node-LL1/lon",
                "2048"),
            preciseErrorCase(
                "lane data attribute child",
                validItwgTimJsonWithClosedPath(),
                xyNodePath + "/attributes",
                "{\"data\":[{\"laneAngle\":181}]}",
                xyNodePath + "/attributes/data/0/laneAngle"),
            preciseErrorCase(
                "computed-lane axis child",
                computedLaneTimJson(),
                regionPath
                    + "/description/path/offset/xy/computed/offsetXaxis/small",
                "2048"),
            preciseErrorCase(
                "advisory item child",
                validItwgTimJson(),
                "/value/TravelerInformation/dataFrames/0/content/advisory/0/item/itis",
                "65536"),
            preciseErrorCase(
                "road-surface child",
                validItwgTimJson(),
                "/value/TravelerInformation/dataFrames/0/contentNew/frictionInfo"
                    + "/roadSurfaceDescription/asphaltOrTar/type",
                "\"invalid\"")
        );
    }

    /** Builds cases with multiple properties in an exclusive choice object. */
    private static Stream<Arguments> preciseChoiceCardinalityCases() throws Exception {
        String regionPath = "/value/TravelerInformation/dataFrames/0/regions/0";
        String deltaPath = regionPath
            + "/description/path/offset/xy/nodes/0/delta";
        String itemPath =
            "/value/TravelerInformation/dataFrames/0/content/advisory/0/item";
        return Stream.of(
            Arguments.of(
                "multiple region description representations",
                descriptionChoiceConflictTimJson(),
                regionPath + "/description"),
            preciseErrorCase(
                "multiple node delta representations",
                validItwgTimJsonWithClosedPath(),
                deltaPath + "/node-XY2",
                "{\"x\":0,\"y\":0}",
                deltaPath),
            preciseErrorCase(
                "multiple advisory item representations",
                validItwgTimJson(),
                itemPath + "/text",
                "\"extra\"",
                itemPath)
        );
    }

    /** Builds forbidden-field cases for circle, open-path, and closed-path regions. */
    private static Stream<Arguments> preciseRegionErrorCases() throws Exception {
        String regionPath = "/value/TravelerInformation/dataFrames/0/regions/0";
        return Stream.of(
            preciseErrorCase(
                "circle with path-only field",
                validItwgTimJson(),
                regionPath + "/closedPath",
                "false"),
            preciseErrorCase(
                "open path with closed-path field",
                itwgTimJsonWithNodeAttributes(NodeEncoding.OPEN_XY, null),
                regionPath + "/direction",
                "\"0000\""),
            preciseErrorCase(
                "closed path with open-path field",
                validItwgTimJsonWithClosedPath(),
                regionPath + "/laneWidth",
                "1")
        );
    }

    /** Creates an argument whose expected error is at the mutated field itself. */
    private static Arguments preciseErrorCase(
            String scenario,
            String json,
            String fieldPath,
            String invalidValueJson) throws Exception {
        return preciseErrorCase(
            scenario,
            json,
            fieldPath,
            invalidValueJson,
            fieldPath);
    }

    /** Creates an argument with separate mutation and expected-error paths. */
    private static Arguments preciseErrorCase(
            String scenario,
            String json,
            String fieldPath,
            String invalidValueJson,
            String expectedPath) throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree(json);
        int fieldSeparator = fieldPath.lastIndexOf('/');
        String parentPath = fieldPath.substring(0, fieldSeparator);
        String fieldName = fieldPath.substring(fieldSeparator + 1);
        ObjectNode parent = (ObjectNode) root.at(parentPath);
        parent.set(fieldName, MAPPER.readTree(invalidValueJson));
        return Arguments.of(scenario, MAPPER.writeValueAsString(root), expectedPath);
    }

    /**
     * Asserts that validation succeeds or reports exactly one ITWG schema issue
     * at the expected attribute path, according to the test case.
     */
    private static void assertAttributeValidation(
            String expectedErrorPath,
            Executable validation) {
        if (expectedErrorPath == null) {
            assertDoesNotThrow(validation);
            return;
        }

        ValidationException ex =
            assertThrows(ValidationException.class, validation);
        assertSingleIssueAt(ex, expectedErrorPath);
    }

    /** Asserts that schema validation returned one issue at the expected path. */
    private static void assertSingleIssueAt(
            ValidationException exception,
            String expectedPath) {
        assertEquals(1, exception.getIssues().size(), () -> exception.getIssues().toString());
        assertEquals("ITWG Schema Validation", exception.getIssues().getFirst().checkName());
        assertEquals(expectedPath, exception.getIssues().getFirst().path());
    }

    /**
     * Builds a valid TIM fixture for the requested node encoding and optionally
     * adds an attributes object to its first node.
     */
    private static String itwgTimJsonWithNodeAttributes(
            NodeEncoding encoding,
            String attributesJson) throws Exception {
        String json = validItwgTimJsonWithClosedPath();
        if (encoding.offsetName().equals("ll")) {
            json = json
                .replace("\"xy\"", "\"ll\"")
                .replace("\"node-XY1\"", "\"node-LL1\"")
                .replace("\"x\"", "\"lon\"")
                .replace("\"y\"", "\"lat\"");
        }

        ObjectNode root = (ObjectNode) MAPPER.readTree(json);
        ObjectNode region = (ObjectNode) root.at(
            "/value/TravelerInformation/dataFrames/0/regions/0");
        if (!encoding.closedPath()) {
            region.put("laneWidth", 1);
            region.put("directionality", "forward");
            region.put("closedPath", false);
            region.remove("direction");
        }

        if (attributesJson != null) {
            ObjectNode firstNode =
                (ObjectNode) root.at(encoding.firstNodePath());
            JsonNode attributes = MAPPER.readTree(attributesJson);
            firstNode.set("attributes", attributes);
        }
        return MAPPER.writeValueAsString(root);
    }

    private enum NodeEncoding {
        OPEN_XY(false, "xy"),
        OPEN_LL(false, "ll"),
        CLOSED_XY(true, "xy"),
        CLOSED_LL(true, "ll");

        private final boolean closedPath;
        private final String offsetName;

        /**
         * Records whether the fixture uses a closed path and which node offset
         * representation it contains.
         */
        NodeEncoding(boolean closedPath, String offsetName) {
            this.closedPath = closedPath;
            this.offsetName = offsetName;
        }

        /** Returns whether this fixture represents a closed path. */
        boolean closedPath() {
            return closedPath;
        }

        /** Returns the JSON field name for the node offset representation. */
        String offsetName() {
            return offsetName;
        }

        /** Returns the JSON Pointer to the fixture's first node. */
        String firstNodePath() {
            return "/value/TravelerInformation/dataFrames/0/regions/0"
                + "/description/path/offset/" + offsetName + "/nodes/0";
        }
    }

    private static String validItwgTimJson() {
        return """
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
                      "msgId": {
                        "furtherInfoID": "0000"
                      },
                      "startYear": 2026,
                      "startTime": 1,
                      "durationTime": 60,
                      "priority": 4,
                      "doNotUse2": 0,
                      "regions": [
                        {
                          "anchor": {
                            "lat": 0,
                            "long": 0,
                            "elevation": 0
                          },
                          "description": {
                            "geometry": {
                              "direction": "0000",
                              "circle": {
                                "center": {
                                  "lat": 0,
                                  "long": 0,
                                  "elevation": 0
                                },
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
                          {
                            "item": {
                              "itis": 268
                            }
                          }
                        ]
                      },
                      "contentNew": {
                        "frictionInfo": {
                          "roadSurfaceDescription": {
                            "asphaltOrTar": {
                              "type": "traveled"
                            }
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
            """;
    }

    private static String validItwgTimJsonWithoutContentNew() {
        return validItwgTimJson().replace(
            """
                      },
                      "contentNew": {
                        "frictionInfo": {
                          "roadSurfaceDescription": {
                            "asphaltOrTar": {
                              "type": "traveled"
                            }
                          }
                        }
                      }
            """,
            """
                      }
            """
        );
    }

    private static String validItwgTimJsonWithClosedPath() {
        return itwgTimJsonWithClosedPathWithoutDirection().replace(
            "\"closedPath\": true,",
            "\"closedPath\": true,\n                          \"direction\": \"0000\","
        );
    }

    /** Builds a schema-valid path that uses a computed XY lane. */
    private static String computedLaneTimJson() throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree(validItwgTimJsonWithClosedPath());
        ObjectNode xy = (ObjectNode) root.at(
            "/value/TravelerInformation/dataFrames/0/regions/0"
                + "/description/path/offset/xy");
        xy.remove("nodes");
        xy.set("computed", MAPPER.readTree("""
            {
              "referenceLaneId": 1,
              "offsetXaxis": {"small": 0},
              "offsetYaxis": {"small": 0}
            }
            """));
        return MAPPER.writeValueAsString(root);
    }

    /** Builds a region description that incorrectly contains path and geometry. */
    private static String descriptionChoiceConflictTimJson() throws Exception {
        ObjectNode circleRoot = (ObjectNode) MAPPER.readTree(validItwgTimJson());
        JsonNode path = MAPPER.readTree(validItwgTimJsonWithClosedPath()).at(
            "/value/TravelerInformation/dataFrames/0/regions/0/description/path");
        ObjectNode description = (ObjectNode) circleRoot.at(
            "/value/TravelerInformation/dataFrames/0/regions/0/description");
        description.set("path", path);
        return MAPPER.writeValueAsString(circleRoot);
    }

    private static String itwgTimJsonWithThreePointClosedPath() {
        return validItwgTimJsonWithClosedPath().replace(
            """
                                    },
                                    {
                                      "delta": {
                                        "node-XY1": {
                                          "x": 0,
                                          "y": -1
                                        }
                                      }
                                    }
            """,
            """
                                    }
            """
        );
    }

    private static String itwgTimJsonWithClosedPathWithoutDirection() {
        return validItwgTimJson().replace(
            """
                          "anchor": {
                            "lat": 0,
                            "long": 0,
                            "elevation": 0
                          },
                          "description": {
                            "geometry": {
                              "direction": "0000",
                              "circle": {
                                "center": {
                                  "lat": 0,
                                  "long": 0,
                                  "elevation": 0
                                },
                                "radius": 1,
                                "units": "meter"
                              }
                            }
                          }
            """,
            """
                          "anchor": {
                            "lat": 0,
                            "long": 0,
                            "elevation": 0
                          },
                          "closedPath": true,
                          "description": {
                            "path": {
                              "offset": {
                                "xy": {
                                  "nodes": [
                                    {
                                      "delta": {
                                        "node-XY1": {
                                          "x": 0,
                                          "y": 0
                                        }
                                      }
                                    },
                                    {
                                      "delta": {
                                        "node-XY1": {
                                          "x": 1,
                                          "y": 1
                                        }
                                      }
                                    },
                                    {
                                      "delta": {
                                        "node-XY1": {
                                          "x": 1,
                                          "y": 0
                                        }
                                      }
                                    },
                                    {
                                      "delta": {
                                        "node-XY1": {
                                          "x": 0,
                                          "y": -1
                                        }
                                      }
                                    }
                                  ]
                                }
                              }
                            }
                          }
            """
        );
    }
}
