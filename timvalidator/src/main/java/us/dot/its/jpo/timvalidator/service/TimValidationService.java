package us.dot.its.jpo.timvalidator.service;

import us.dot.its.jpo.timvalidator.converter.UperToJerConverter;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.pojo.ValidationResult;
import us.dot.its.jpo.timvalidator.validator.BestPracticesValidator;
import us.dot.its.jpo.timvalidator.validator.SchemaValidator;

/**
 * Main service orchestrating the TIM validation pipeline.
 * 
 * Process flow:
 * 1. Convert UPER string to JER format
 * 2. Deserialize JER into POJO using jpo-asn-runtime
 * 3. Perform schema validation against JSON schemas
 * 4. Execute best practices checks
 * 5. Return processed validation result
 */
public class TimValidationService {

    private final UperToJerConverter uperToJerConverter;
    private final SchemaValidator schemaValidator;
    private final BestPracticesValidator bestPracticesValidator;

    public TimValidationService() {
        this.uperToJerConverter = new UperToJerConverter();
        this.schemaValidator = new SchemaValidator();
        this.bestPracticesValidator = new BestPracticesValidator();
    }

    /**
     * Validates a TIM message from UPER format.
     *
     * @param uperString the UPER encoded TIM message
     * @return ValidationResult containing validation status and details
     * @throws ValidationException if validation fails critically
     */
    public ValidationResult validateTim(String uperString) throws ValidationException {
        ValidationResult result = new ValidationResult();

        try {
            // Step 1: Convert UPER to JER
            String jerFormat = uperToJerConverter.convertUperToJer(uperString);
            result.setJerFormat(jerFormat);

            // Step 2: Deserialize into POJO
            Object timMessage = uperToJerConverter.deserializeToObject(jerFormat);
            result.setTimMessage(timMessage);

            // Step 3: Perform schema validation
            schemaValidator.validate(timMessage);
            result.addValidationCheck("Schema Validation", true, "POJO conforms to schema");

            // Step 4: Perform best practices checks
            java.util.List<String> bestPracticesIssues = bestPracticesValidator.validate(timMessage);
            if (bestPracticesIssues.isEmpty()) {
                result.addValidationCheck("Best Practices", true, "All best practices checks passed");
            } else {
                result.addValidationCheck("Best Practices", false, String.join("; ", bestPracticesIssues));
            }

            result.setValid(true);
        } catch (Exception e) {
            result.setValid(false);
            result.setErrorMessage(e.getMessage());
            throw new ValidationException("TIM validation failed: " + e.getMessage(), e);
        }

        return result;
    }
}
