package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
            boolean expectedValid) throws Exception {
        String json = itwgTimJsonWithNodeAttributes(encoding, attributesJson);
        ItwgTimJsonValidator validator = new ItwgTimJsonValidator();

        assertAttributeValidation(
                expectedValid,
                () -> validator.validateJson(json));

        var messageFrame = new JerToMessageFrameConverter().deserialize(json);
        assertAttributeValidation(
                expectedValid,
                () -> validator.validate(messageFrame));
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
            Arguments.of(encoding, "attributes absent", null, true),
            Arguments.of(encoding, "empty attributes", "{}", false),
            Arguments.of(
                encoding,
                "nonzero width and elevation",
                "{\"dWidth\": 5, \"dElevation\": -5}",
                true),
            Arguments.of(encoding, "zero width", "{\"dWidth\": 0}", false),
            Arguments.of(encoding, "zero elevation", "{\"dElevation\": 0}", false),
            Arguments.of(
                encoding,
                "zero width with nonzero elevation",
                "{\"dWidth\": 0, \"dElevation\": 5}",
                false),
            Arguments.of(
                encoding,
                "nonzero width with zero elevation",
                "{\"dWidth\": 5, \"dElevation\": 0}",
                false),
            Arguments.of(
                encoding,
                "other meaningful attribute",
                "{\"localNode\": [\"stopLine\"]}",
                true)
        ));
    }

    /**
     * Asserts that validation succeeds or reports an ITWG schema issue at the
     * attributes object, according to the test case.
     */
    private static void assertAttributeValidation(
            boolean expectedValid,
            Executable validation) {
        if (expectedValid) {
            assertDoesNotThrow(validation);
            return;
        }

        ValidationException ex =
            assertThrows(ValidationException.class, validation);
        assertTrue(ex.getIssues().stream().anyMatch(issue ->
            issue.checkName().equals("ITWG Schema Validation")
                && issue.path() != null
                && issue.path().contains("/attributes")));
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
