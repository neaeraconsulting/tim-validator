package us.dot.its.jpo.timvalidator.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;

/**
 * Deserializes JER (JSON) format into typed {@code TravelerInformationMessageFrame} POJOs.
 */
public class JerToMessageFrameConverter {

    private static final ThreadLocal<ObjectMapper> JSON_MAPPER = ThreadLocal.withInitial(ObjectMapper::new);

    /**
     * Creates a new {@code JerToMessageFrameConverter}.
     */
    public JerToMessageFrameConverter() {}

    /**
     * Deserializes JER format into a TravelerInformationMessageFrame POJO using Jackson ObjectMapper.
     *
     * @param jerFormat the JER/JSON formatted string
     * @return deserialized TravelerInformationMessageFrame object
     * @throws IllegalArgumentException if the input is null or blank
     * @throws RuntimeException if deserialization fails
     */
    public TravelerInformationMessageFrame deserialize(String jerFormat) {
        if (jerFormat == null || jerFormat.isBlank()) {
            throw new IllegalArgumentException("JER/JSON payload must not be null or blank");
        }

        try {
            ObjectMapper mapper = JSON_MAPPER.get();
            JsonNode rootNode = mapper.readTree(jerFormat);

            // Try to extract MessageFrame from common nested structures
            JsonNode messageFrameNode = extractMessageFrameNode(rootNode);

            if (messageFrameNode == null || messageFrameNode.isNull()) {
                throw new RuntimeException("MessageFrame node not found in JER payload");
            }

            return mapper.convertValue(messageFrameNode, TravelerInformationMessageFrame.class);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to deserialize JER to MessageFrame POJO: " + ex.getMessage(), ex);
        }
    }

    /**
     * Extracts TravelerInformationMessageFrame node from JER JsonNode tree, supporting two nesting patterns.
     *
     * @param rootNode the root JsonNode from parsed JER
     * @return the TravelerInformationMessageFrame JsonNode, or null if not found
     */
    private JsonNode extractMessageFrameNode(JsonNode rootNode) {
        JsonNode node = rootNode.path("MessageFrame");
        if (!node.isMissingNode() && !node.isNull()) {
            return node;
        }

        if (rootNode.has("messageId") || rootNode.has("messageID") || rootNode.has("value")) {
            return rootNode;
        }

        return null;
    }
}
