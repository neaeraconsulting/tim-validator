package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import us.dot.its.jpo.timvalidator.converter.JerToMessageFrameConverter;
import us.dot.its.jpo.timvalidator.exception.ValidationException;

class ItwgTimJsonValidatorTest {

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

    @Test
    void validateJson_polygonRegionWithoutDirection_passesValidation() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();

        assertDoesNotThrow(() -> itwgValidator.validateJson(validItwgTimJsonWithPolygonWithoutDirection()));
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
    void validateJson_polygonRegionWithOpenPathFields_throwsValidationException() {
        ItwgTimJsonValidator itwgValidator = new ItwgTimJsonValidator();
        String invalidJson = validItwgTimJsonWithPolygonWithoutDirection().replace(
            """
                          "closedPath": true,
                          "description": {
            """,
            """
                          "closedPath": true,
                          "laneWidth": 1,
                          "directionality": "forward",
                          "description": {
            """
        );

        ValidationException ex = assertThrows(ValidationException.class,
            () -> itwgValidator.validateJson(invalidJson));

        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.message().contains("laneWidth")));
        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.message().contains("directionality")));
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

    private static String validItwgTimJsonWithPolygonWithoutDirection() {
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
