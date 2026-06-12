package us.dot.its.jpo.timvalidator.itis;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;

import us.dot.its.jpo.timvalidator.exception.ValidationException;

/**
 * Validates normalized TIM ITIS message content with JSON Schema.
 *
 * <p>
 * The input to this validator is already normalized into a small JSON object,
 * for example {@code {"itis":[6937,12599],"priority":4,...}}. This class does
 * not
 * decode ASN.1 or interpret token meanings itself; it owns the structural,
 * range, and associated TIM metadata checks from {@code ITISCodes.json}.
 * Invalid content raises a ValidationException with the schema failure details.
 *
 * <p>
 * This currently restricts gross weight limits to a single large-number
 * token, instead of also allowing an additional small-number for
 * more specific numbers. (ex. N25000 can be validated but N25000, N123 cannot)
 */
public class ItisContentValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Classpath location of the JSON Schema that lists the accepted ITIS token patterns.
    private static final String DEFAULT_SCHEMA_RESOURCE =
            "/us/dot/its/jpo/timvalidator/ITISCodes.json";

    private final Schema schema;

    /**
     * Builds a validator using the default ITIS token sequence schema bundled
     * under src/main/resources.
     */
    public ItisContentValidator() throws ValidationException {
        this(DEFAULT_SCHEMA_RESOURCE);
    }

    /**
     * Builds a validator from a custom classpath schema resource. This is useful
     * for tests or for validating against an alternate ITIS token catalog.
     */
    public ItisContentValidator(String schemaResource) throws ValidationException {
        this.schema = loadSchema(schemaResource);
    }

    /**
     * Parses raw JSON text and validates it against the ITIS schema.
     */
    public void validate(String json) throws ValidationException {
        try {
            validate(MAPPER.readTree(json));
        } catch (IOException ex) {
            throw new ValidationException("Invalid JSON content", ex);
        }
    }

    /**
     * Converts any Java object into a Jackson tree before validation. Callers can
     * pass maps, records, or POJOs without manually serializing them first.
     */
    public void validate(Object content) throws ValidationException {
        validate(MAPPER.valueToTree(content));
    }

    /**
     * Validates an already parsed Jackson tree. This avoids an extra parse step
     * when upstream code is already working with JsonNode content.
     */
    public void validate(JsonNode content) throws ValidationException {
        List<Error> messages = schema.validate(content);
        if (!messages.isEmpty()) {
            throw new ValidationException(toErrorMessage(messages));
        }
    }

    private Schema loadSchema(String schemaResource) throws ValidationException {
        try (InputStream stream = ItisContentValidator.class.getResourceAsStream(schemaResource)) {
            if (stream == null) {
                throw new ValidationException("JSON Schema resource not found: " + schemaResource);
            }

            SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                    .cacheRefs(true)
                    .failFast(false)
                    .pathType(PathType.JSON_POINTER)
                    .losslessNarrowing(true)
                    .build();
            SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                    SpecificationVersion.DRAFT_7,
                    builder -> builder.schemaRegistryConfig(config));
            return registry.getSchema(stream, InputFormat.JSON);
        } catch (IOException ex) {
            throw new ValidationException("Unable to load JSON Schema: " + schemaResource, ex);
        }
    }

    private String toErrorMessage(List<Error> messages) {
        return messages.stream()
            .map(message -> message.getInstanceLocation() + ": " + message.getMessage())
            .reduce((left, right) -> left + "; " + right)
            .orElseThrow(() -> new IllegalArgumentException("Validation error messages cannot be empty"));
    }
}
