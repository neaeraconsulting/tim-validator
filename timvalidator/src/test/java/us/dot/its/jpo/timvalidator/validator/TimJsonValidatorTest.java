package us.dot.its.jpo.timvalidator.validator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.timvalidator.converter.UperToMessageFrameConverter;
import us.dot.its.jpo.timvalidator.exception.ValidationException;

class TimJsonValidatorTest {

  private static final String LEGACY_UPER_MISSING_ITWG_FIELDS_HEX =
        "001F6970138ED764E8ABE0BBA9B4D5240F775D9B0309C269A6E4D166420B77FFF93F51D3C5801EA107F92937E4AD64D6FD38352FB783062C360DE24000000004D34DC9A2CC8416E271180004420C0F23A84179FF2461BE25D59F405F03B8C82F1574AE109002009EEEBB36006001830002848A859B4B280002848AF0E51D2881010100030180C620FB90CAAD3B9C50820826550919D5729A7639692100032A3649C88400A983010180034801010001838182D6DDACDEEEE30D5990CA8E531F4562161223F5418FD9A82BE7219686AA70CD938080BE6942DDAC14F4007CC8F8BD6CAEA835F02C7BBA3354ED2856E5977879ECEF5205A37A1CD9A26E12A6CFF6550202138D3F5CA0D3AE158B18895F0BBF16176971";

    private static final String ITWG_VALID_UPER_HEX = "001f3c6010000100000000000000000018080000fd40000200f2000424d693a401ad2747fc40008000026b49d200d693a3fe2000002c000000218020402200";

    @Test
    void validate_decodedTimMessageMissingItwgFields_passesJ2735Validation() throws Exception {
        Assumptions.assumeTrue(isNativeLibraryAvailable(),
            "Native codec library not found; skipping test");

        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();
        String xer = converter.convertUperToXer(LEGACY_UPER_MISSING_ITWG_FIELDS_HEX);
        TravelerInformationMessageFrame messageFrame = converter.deserialize(xer);

        TimJsonValidator timJsonValidator = new TimJsonValidator();

        assertDoesNotThrow(() -> timJsonValidator.validate(messageFrame));
      }

      @Test
      void validate_sampleUperMessage_passesValidation() throws Exception {
        Assumptions.assumeTrue(isNativeLibraryAvailable(),
            "Native codec library not found; skipping test");

        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();
        String xer = converter.convertUperToXer(ITWG_VALID_UPER_HEX);
        TravelerInformationMessageFrame messageFrame = converter.deserialize(xer);

        TimJsonValidator timJsonValidator = new TimJsonValidator();

        assertDoesNotThrow(() -> timJsonValidator.validate(messageFrame));
    }

    @Test
    void validate_nullPayload_throwsValidationException() {
        TimJsonValidator timJsonValidator = new TimJsonValidator();

        ValidationException ex = assertThrows(ValidationException.class, () -> timJsonValidator.validate(null));

        assertTrue(ex.getMessage().contains("cannot be null"));
    }

    @Test
    void validate_nonMessageFramePayload_throwsValidationException() {
        TimJsonValidator timJsonValidator = new TimJsonValidator();

        ValidationException ex = assertThrows(ValidationException.class, () -> timJsonValidator.validate("not-a-message-frame"));

        assertTrue(ex.getMessage().contains("Expected MessageFrame payload"));
    }

    @Test
    void validateJson_blankPayload_throwsValidationException() {
        TimJsonValidator timJsonValidator = new TimJsonValidator();

        ValidationException ex = assertThrows(ValidationException.class, () -> timJsonValidator.validateJson("   "));

        assertTrue(ex.getMessage().contains("cannot be null or blank"));
    }

    @Test
    void validateJson_invalidJson_throwsValidationException() {
        TimJsonValidator timJsonValidator = new TimJsonValidator();

        ValidationException ex = assertThrows(ValidationException.class, () -> timJsonValidator.validateJson("{invalid-json"));

        assertTrue(ex.getMessage().contains("Failed to validate JSON payload"));
    }

    @Test
    void validateJson_schemaViolation_throwsValidationException() {
        TimJsonValidator timJsonValidator = new TimJsonValidator();

        ValidationException ex = assertThrows(ValidationException.class, () -> timJsonValidator.validateJson("{}"));

        assertTrue(ex.getMessage().contains("Schema validation failed"));
        assertTrue(!ex.getIssues().isEmpty());
        assertTrue(ex.getIssues().stream().allMatch(issue -> issue.path() != null));
        assertTrue(ex.getIssues().stream().allMatch(issue -> issue.message() != null));
        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.checkName().equals("J2735 Schema Validation")
            && issue.message().contains("messageId")));
    }

    @Test
    void validateJson_validItwgTim_passesValidation() {
      TimJsonValidator timJsonValidator = new TimJsonValidator();

      assertDoesNotThrow(() -> timJsonValidator.validateJson("""
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
          """));
    }

    @Test
    void validateJson_decodedTimMessageMissingItwgFields_passesJ2735Validation() throws Exception {
        Assumptions.assumeTrue(isNativeLibraryAvailable(),
            "Native codec library not found; skipping test");

        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();
        String xer = converter.convertUperToXer(LEGACY_UPER_MISSING_ITWG_FIELDS_HEX);
        TravelerInformationMessageFrame messageFrame = converter.deserialize(xer);
        String jsonPayload = new ObjectMapper().writeValueAsString(messageFrame);

        TimJsonValidator timJsonValidator = new TimJsonValidator();

        assertDoesNotThrow(() -> timJsonValidator.validateJson(jsonPayload));
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
