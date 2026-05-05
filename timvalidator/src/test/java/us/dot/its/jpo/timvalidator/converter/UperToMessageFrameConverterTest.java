package us.dot.its.jpo.timvalidator.converter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;

class UperToMessageFrameConverterTest {

    private static final String VALID_UPER_HEX =
        "001338000817a780000089680500204642b342b34802021a15a955a940181190acd0acd20100868555c555c00104342aae2aae002821a155715570";

    @Test
    void convertUperToXer_validPayload_returnsXerXml() {
        Assumptions.assumeTrue(isNativeLibraryAvailable(),
            "Native codec library not found; skipping conversion integration assertion");

        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();

        String xer = converter.convertUperToXer(VALID_UPER_HEX);

        assertNotNull(xer, "Converted XER should not be null");
        assertTrue(!xer.isBlank(), "Converted XER should not be blank");
        assertTrue(xer.contains("<"), "Converted payload should contain XML markers");
        assertTrue(xer.contains(">"), "Converted payload should contain XML markers");
    }

    @Test
    void convertUperToXer_nullInput_throwsIllegalArgumentException() {
        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> converter.convertUperToXer(null)
        );

        assertTrue(ex.getMessage().contains("must not be null or blank"));
    }

    @Test
    void convertUperToXer_invalidHex_throwsIllegalArgumentException() {
        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> converter.convertUperToXer("this-is-not-hex")
        );

        assertTrue(ex.getMessage().contains("not valid hexadecimal"));
    }

    @Test
    void deserializeToObject_fromUperHex_returnsPopulatedMessageFrame() {
        Assumptions.assumeTrue(isNativeLibraryAvailable(),
            "Native codec library not found; skipping deserialization integration assertion");

        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();
        String xer = converter.convertUperToXer(VALID_UPER_HEX);

        MessageFrame<?> messageFrame = converter.deserializeToObject(xer);

        assertNotNull(messageFrame, "Deserialized MessageFrame should not be null");
        assertNotNull(messageFrame.getMessageId(), "MessageFrame messageId should not be null");
        assertNotNull(messageFrame.getValue(), "MessageFrame value should not be null");
    }

    @Test
    void deserializeToObject_nullInput_throwsIllegalArgumentException() {
        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> converter.deserializeToObject(null)
        );

        assertTrue(ex.getMessage().contains("must not be null or blank"));
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
