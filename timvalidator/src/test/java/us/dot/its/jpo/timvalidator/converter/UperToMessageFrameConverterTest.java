package us.dot.its.jpo.timvalidator.converter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;

class UperToMessageFrameConverterTest {

    private static final String VALID_UPER_HEX =
        "001F6970138ED764E8ABE0BBA9B4D5240F775D9B0309C269A6E4D166420B77FFF93F51D3C5801EA107F92937E4AD64D6FD38352FB783062C360DE24000000004D34DC9A2CC8416E271180004420C0F23A84179FF2461BE25D59F405F03B8C82F1574AE109002009EEEBB36006001830002848A859B4B280002848AF0E51D2881010100030180C620FB90CAAD3B9C50820826550919D5729A7639692100032A3649C88400A983010180034801010001838182D6DDACDEEEE30D5990CA8E531F4562161223F5418FD9A82BE7219686AA70CD938080BE6942DDAC14F4007CC8F8BD6CAEA835F02C7BBA3354ED2856E5977879ECEF5205A37A1CD9A26E12A6CFF6550202138D3F5CA0D3AE158B18895F0BBF16176971";

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
    void deserialize_fromUperHex_returnsPopulatedMessageFrame() {
        Assumptions.assumeTrue(isNativeLibraryAvailable(),
            "Native codec library not found; skipping deserialization integration assertion");

        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();
        String xer = converter.convertUperToXer(VALID_UPER_HEX);

        TravelerInformationMessageFrame messageFrame = converter.deserialize(xer);

        // Basic assertions to verify deserialization worked and produced expected content for a J2735 TIM
        assertNotNull(messageFrame, "Deserialized MessageFrame should not be null");
        assertNotNull(messageFrame.getMessageId(), "MessageFrame messageId should not be null");
        assertEquals(31, messageFrame.getMessageId().getValue(), "MessageFrame messageId should be 31");
        assertNotNull(messageFrame.getValue(), "MessageFrame value should not be null");
    }

    @Test
    void deserialize_nullInput_throwsIllegalArgumentException() {
        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> converter.deserialize(null)
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
