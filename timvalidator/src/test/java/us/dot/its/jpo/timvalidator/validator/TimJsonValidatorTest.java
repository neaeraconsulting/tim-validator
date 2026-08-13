package us.dot.its.jpo.timvalidator.validator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static us.dot.its.jpo.timvalidator.validator.ItwgTimTestFixtures.circleTimJson;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.timvalidator.converter.UperToMessageFrameConverter;
import us.dot.its.jpo.timvalidator.exception.ValidationException;

class TimJsonValidatorTest {

    private static final TimJsonValidator VALIDATOR = new TimJsonValidator();
    private static final String LEGACY_UPER_MISSING_ITWG_FIELDS_HEX =
        "001F6970138ED764E8ABE0BBA9B4D5240F775D9B0309C269A6E4D166420B77FFF93F51D3C5801EA1"
            + "07F92937E4AD64D6FD38352FB783062C360DE24000000004D34DC9A2CC8416E271180004420C0F23"
            + "A84179FF2461BE25D59F405F03B8C82F1574AE109002009EEEBB36006001830002848A859B4B2800"
            + "02848AF0E51D2881010100030180C620FB90CAAD3B9C50820826550919D5729A7639692100032A36"
            + "49C88400A983010180034801010001838182D6DDACDEEEE30D5990CA8E531F4562161223F5418FD9"
            + "A82BE7219686AA70CD938080BE6942DDAC14F4007CC8F8BD6CAEA835F02C7BBA3354ED2856E59778"
            + "79ECEF5205A37A1CD9A26E12A6CFF6550202138D3F5CA0D3AE158B18895F0BBF16176971";

    private static final String ITWG_VALID_UPER_HEX =
        "001f3c6010000100000000000000000018080000fd40000200f2000424d693a401ad2747fc400080"
            + "00026b49d200d693a3fe2000002c000000218020402200";

    /** Verifies that base J2735 validation accepts decoded legacy TIMs. */
    @Test
    void validate_decodedTimMessageMissingItwgFields_passesJ2735Validation() throws Exception {
        Assumptions.assumeTrue(isNativeLibraryAvailable(),
            "Native codec library not found; skipping test");

        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();
        String xer = converter.convertUperToXer(LEGACY_UPER_MISSING_ITWG_FIELDS_HEX);
        TravelerInformationMessageFrame messageFrame = converter.deserialize(xer);

        assertDoesNotThrow(() -> VALIDATOR.validate(messageFrame));
    }

    /** Verifies that a known valid UPER TIM passes base J2735 validation. */
    @Test
    void validate_sampleUperMessage_passesValidation() throws Exception {
        Assumptions.assumeTrue(isNativeLibraryAvailable(),
            "Native codec library not found; skipping test");

        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();
        String xer = converter.convertUperToXer(ITWG_VALID_UPER_HEX);
        TravelerInformationMessageFrame messageFrame = converter.deserialize(xer);

        assertDoesNotThrow(() -> VALIDATOR.validate(messageFrame));
    }

    /** Verifies the null guard on deserialized payload validation. */
    @Test
    void validate_nullPayload_throwsValidationException() {
        ValidationException ex = assertThrows(ValidationException.class, () -> VALIDATOR.validate(null));

        assertTrue(ex.getMessage().contains("cannot be null"));
    }

    /** Verifies that base TIM validation rejects unrelated payload types. */
    @Test
    void validate_nonMessageFramePayload_throwsValidationException() {
        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> VALIDATOR.validate("not-a-message-frame"));

        assertTrue(ex.getMessage().contains("Expected MessageFrame payload"));
    }

    /** Verifies the blank-input guard on raw JSON validation. */
    @Test
    void validateJson_blankPayload_throwsValidationException() {
        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> VALIDATOR.validateJson("   "));

        assertTrue(ex.getMessage().contains("cannot be null or blank"));
    }

    /** Verifies that malformed JSON produces a conversion failure. */
    @Test
    void validateJson_invalidJson_throwsValidationException() {
        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> VALIDATOR.validateJson("{invalid-json"));

        assertTrue(ex.getMessage().contains("Failed to validate JSON payload"));
    }

    /** Verifies that base schema failures contain structured issue details. */
    @Test
    void validateJson_schemaViolation_throwsValidationException() {
        ValidationException ex = assertThrows(
            ValidationException.class,
            () -> VALIDATOR.validateJson("{}"));

        assertTrue(ex.getMessage().contains("Schema validation failed"));
        assertFalse(ex.getIssues().isEmpty());
        assertTrue(ex.getIssues().stream().allMatch(issue -> issue.path() != null));
        assertTrue(ex.getIssues().stream().allMatch(issue -> issue.message() != null));
        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.checkName().equals("J2735 Schema Validation")
            && issue.message().contains("messageId")));
    }

    /** Verifies that the shared valid TIM fixture also satisfies the base schema. */
    @Test
    void validateJson_validItwgTim_passesValidation() {
        assertDoesNotThrow(() -> VALIDATOR.validateJson(circleTimJson()));
    }

    /** Verifies that a decoded TIM can be reserialized and schema-validated. */
    @Test
    void validateJson_decodedTimMessageMissingItwgFields_passesJ2735Validation() throws Exception {
        Assumptions.assumeTrue(isNativeLibraryAvailable(),
            "Native codec library not found; skipping test");

        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();
        String xer = converter.convertUperToXer(LEGACY_UPER_MISSING_ITWG_FIELDS_HEX);
        TravelerInformationMessageFrame messageFrame = converter.deserialize(xer);
        String jsonPayload = new ObjectMapper().writeValueAsString(messageFrame);

        assertDoesNotThrow(() -> VALIDATOR.validateJson(jsonPayload));
    }

    /** Detects whether the platform-specific native UPER codec is available. */
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
