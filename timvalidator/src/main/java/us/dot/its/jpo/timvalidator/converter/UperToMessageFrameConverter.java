package us.dot.its.jpo.timvalidator.converter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import j2735ffm.MessageFrameCodec;
import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;

/**
 * Converts UPER-encoded ASN.1 messages to XER (XML) format and deserializes
 * the resulting XML into typed {@code MessageFrame} POJOs.
 */
public class UperToMessageFrameConverter {

    private static final long ENCODE_BUFFER_SIZE = 262144L;
    private static final long DECODE_BUFFER_SIZE = 8192L;
    private static final long STRING_CACHE_SIZE = 512L;
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private static final ThreadLocal<MessageFrameCodec> CODEC = ThreadLocal.withInitial(() -> {
        try {
            return new MessageFrameCodec(
                ENCODE_BUFFER_SIZE,
                DECODE_BUFFER_SIZE,
                STRING_CACHE_SIZE,
                resolveLibraryPath()
            );
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize MessageFrameCodec", ex);
        }
    });

    private static final ThreadLocal<XmlMapper> XML_MAPPER = ThreadLocal.withInitial(XmlMapper::new);

    /**
     * Converts an UPER encoded string to XER format using the J2735 FFM library.
     * 
     * @param uperString the UPER encoded message (typically hex string)
     * @return XER formatted string (XML)
     * @throws IllegalArgumentException if the input is null, blank, or not valid hexadecimal
     */
    public String convertUperToXer(String uperString) {
        if (uperString == null || uperString.isBlank()) {
            throw new IllegalArgumentException("UPER string must not be null or blank");
        }

        String normalizedHex = stripWhitespace(uperString);
        byte[] uperBytes;
        try {
            uperBytes = HEX_FORMAT.parseHex(normalizedHex);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("UPER string is not valid hexadecimal", ex);
        }

        return CODEC.get().uperToXer(uperBytes);
    }

    /**
     * Deserializes XER format into a MessageFrame POJO using Jackson XmlMapper.
     * 
     * @param xerFormat the XER/XML formatted string
     * @return deserialized MessageFrame object
     * @throws IllegalArgumentException if the input is null or blank
     * @throws RuntimeException if deserialization fails
     */
    public MessageFrame<?> deserializeToObject(String xerFormat) {
        if (xerFormat == null || xerFormat.isBlank()) {
            throw new IllegalArgumentException("XER/XML payload must not be null or blank");
        }

        try {
            XmlMapper mapper = XML_MAPPER.get();
            JsonNode rootNode = mapper.readTree(xerFormat);

            // Try to extract MessageFrame from common nested structures
            JsonNode messageFrameNode = extractMessageFrameNode(rootNode);

            if (messageFrameNode == null || messageFrameNode.isNull()) {
                throw new RuntimeException("MessageFrame node not found in XER payload");
            }

            return mapper.convertValue(messageFrameNode, MessageFrame.class);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to deserialize XER to MessageFrame POJO: " + ex.getMessage(), ex);
        }
    }

    /**
     * Extracts MessageFrame node from XER JsonNode tree, supporting two nesting patterns.
     * 
     * @param rootNode the root JsonNode from parsed XER
     * @return the MessageFrame JsonNode, or null if not found
     */
    private JsonNode extractMessageFrameNode(JsonNode rootNode) {
        JsonNode node = rootNode.path("MessageFrame");
        if (!node.isMissingNode() && !node.isNull()) {
            return node;
        }

        if (rootNode.has("messageID") || rootNode.has("value")) {
            return rootNode;
        }

        return null;
    }

    private static Path resolveLibraryPath() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String fileName = osName.contains("win") ? "asnapplication.dll" : "libasnapplication.so";

        Path[] candidates = new Path[] {
            Paths.get(fileName),
            Paths.get("target", "libs", fileName),
            Paths.get("libs", fileName)
        };

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }

        return candidates[0];
    }

    private static String stripWhitespace(String value) {
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c)) {
                if (sb == null) {
                    sb = new StringBuilder(value.length());
                    sb.append(value, 0, i);
                }
                continue;
            }
            if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? value : sb.toString();
    }
}
