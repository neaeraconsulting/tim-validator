package us.dot.its.jpo.timvalidator.validator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;

/**
 * Base validator for schema-backed POJO and JSON validation.
 */
public abstract class AbstractJsonValidator {

    
    private final ObjectMapper mapper = new ObjectMapper();
    private final Resource schemaResource;
    private volatile Schema schema;

    /**
     * Constructs an AbstractJsonValidator with the specified JSON schema resource.
     * @param schemaResource the Spring Resource pointing to the JSON schema file
     */
    protected AbstractJsonValidator(Resource schemaResource) {
        this.schemaResource = schemaResource;
    }

    /**
     * Validates an already-deserialized payload by converting it to a JsonNode for schema validation.
     * @param payload the deserialized payload to validate against the JSON schema
     * @throws ValidationException if the payload is null or fails schema validation
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
     * @param jsonString the raw JSON string to validate against the JSON schema
     * @throws ValidationException if the JSON string is null, blank, or fails schema validation
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
     * Validates an already parsed Jackson tree.
     * @param node the Jackson JsonNode to validate against the JSON schema
     * @throws ValidationException if the node is null or fails schema validation
     */
    public void validate(JsonNode node) throws ValidationException {
        if (node == null || node.isNull()) {
            throw new ValidationException("JSON payload cannot be null");
        }

        validateNodeAgainstSchema(node);
    }

    /**
     * Exposes the compiled schema for testing. This method is a public wrapper around the private getOrCreateSchema() function. 
     * @return the compiled JSON schema instance
     * @throws Exception if there is an error creating or retrieving the JSON schema
     * @see #getOrCreateSchema()
     */
    public Schema getJsonSchema() throws Exception {
        return getOrCreateSchema();
    }

    /**
     * Centralizes schema error aggregation so callers receive a single actionable validation message.
     * @param node the JSON node to validate against the schema
     * @throws ValidationException if the node fails schema validation
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
            throw new ValidationException(formatValidationErrors(validationErrors), toValidationIssues(validationErrors));
        }
    }

    /**
     * Converts schema validation errors into structured issues for API and UI consumers.
     * @param validationErrors the list of schema validation errors to convert
     * @return a list of structured validation issues representing the errors
     */
    protected List<ValidationIssue> toValidationIssues(List<Error> validationErrors) {
        return validationErrors.stream()
            .map(error -> new ValidationIssue(
                ValidationSeverity.ERROR,
                getCheckName(),
                error.getMessage(),
                String.valueOf(error.getInstanceLocation())))
            .toList();
    }

    /**
     * Identifies this validator in structured validation issues.
     * @return the name of the check associated with this validator
     */
    protected String getCheckName() {
        return "Schema Validation";
    }

    /**
     * Formats schema validation errors for callers. Subclasses may override this
     * when a validator needs domain-specific wording.
     * @param validationErrors the list of schema validation errors to format
     * @return a formatted string representing the validation errors
     */
    protected String formatValidationErrors(List<Error> validationErrors) {
        StringBuilder message = new StringBuilder("Schema validation failed:");
        for (Error error : validationErrors) {
            message.append(System.lineSeparator()).append("- ").append(error);
        }
        return message.toString();
    }

    /**
     * Ensures schema compilation happens only once and is safe under concurrent access.
     * @return the compiled JSON schema instance
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
     * @param schemaResource the resource containing the JSON schema
     * @return the compiled JSON schema instance
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
     * @param schemaResource the resource containing the JSON schema
     * @return the JSON schema as a string
     * @throws Exception if an error occurs while reading the schema resource
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
