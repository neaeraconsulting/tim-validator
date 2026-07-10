package us.dot.its.jpo.timvalidator.validator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.Resource;

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
import us.dot.its.jpo.timvalidator.pojo.ValidationFieldError;

/**
 * Base validator for schema-backed POJO and JSON validation.
 */
public abstract class AbstractJsonValidator {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Resource schemaResource;
    private volatile Schema schema;

    protected AbstractJsonValidator(Resource schemaResource) {
        this.schemaResource = schemaResource;
    }

    /**
     * Validates an already-deserialized payload by converting it to a JsonNode for schema validation.
     */
    public void validate(Object payload) throws ValidationException {
        if (payload == null) {
            throw new ValidationException("Payload cannot be null");
        }

        JsonNode messageNode = mapper.valueToTree(payload);
        validateNodeAgainstSchema(messageNode);
    }

    /**
     * Validates raw JSON input directly.
     */
    public void validateJson(String jsonString) throws ValidationException {
        if (jsonString == null || jsonString.isBlank()) {
            throw new ValidationException("JSON payload cannot be null or blank");
        }

        try {
            JsonNode node = mapper.readTree(jsonString);
            validateNodeAgainstSchema(node);
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ValidationException("Failed to validate JSON payload: " + ex.getMessage(), ex);
        }
    }

    /**
     * Exposes the compiled schema for testing.
     */
    public Schema getJsonSchema() throws Exception {
        return getOrCreateSchema();
    }

    /**
     * Centralizes schema error aggregation so callers receive a single actionable validation message.
     */
    protected void validateNodeAgainstSchema(JsonNode node) throws ValidationException {
        List<Error> validationErrors;
        try {
            validationErrors = getOrCreateSchema().validate(node);
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ValidationException("Schema validation execution failed: " + ex.getMessage(), ex);
        }

        if (!validationErrors.isEmpty()) {
            StringBuilder message = new StringBuilder("Schema validation failed:");
            List<ValidationFieldError> fieldErrors = new ArrayList<>();
            for (Error error : validationErrors) {
                message.append(System.lineSeparator()).append("- ").append(error);
                fieldErrors.add(new ValidationFieldError(
                    error.getInstanceLocation().toString(),
                    error.getMessage()
                ));
            }
            throw new ValidationException(message.toString(), fieldErrors);
        }
    }

    /**
     * Ensures schema compilation happens only once and is safe under concurrent access.
     */
    private Schema getOrCreateSchema() throws Exception {
        Schema existingSchema = schema;
        if (existingSchema != null) {
            return existingSchema;
        }

        synchronized (this) {
            if (schema == null) {
                schema = createSchema(schemaResource);
            }
            return schema;
        }
    }

    /**
     * Compiles the JSON schema text into a reusable validator instance.
     */
    private Schema createSchema(Resource schemaResource) {
        String schemaJson;
        try {
            schemaJson = loadSchema(schemaResource);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load schema from resource: " + schemaResource, ex);
        }

        SchemaRegistryConfig config = SchemaRegistryConfig.builder()
            .cacheRefs(true)
            .failFast(false)
            .pathType(PathType.JSON_POINTER)
            .losslessNarrowing(true)
            .build();

        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2019_09,
            builder -> builder
                .schemaRegistryConfig(config)
        );

        try (InputStream schemaStream = new java.io.ByteArrayInputStream(schemaJson.getBytes(StandardCharsets.UTF_8))) {
            return registry.getSchema(schemaStream, InputFormat.JSON);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compile schema from resource: " + schemaResource, ex);
        }
    }

    /**
     * Reads the schema resource into text so subclasses can preprocess it before compilation.
     */
    protected String loadSchema(Resource schemaResource) throws Exception {
        try (InputStream inputStream = schemaResource.getInputStream()) {
            if (inputStream == null) {
                throw new IOException("Schema resource not found: " + schemaResource);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
