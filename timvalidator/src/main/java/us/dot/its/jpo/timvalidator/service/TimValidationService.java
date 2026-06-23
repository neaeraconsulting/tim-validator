package us.dot.its.jpo.timvalidator.service;

import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;
import us.dot.its.jpo.timvalidator.converter.UperToMessageFrameConverter;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.pojo.ValidationResult;
import us.dot.its.jpo.timvalidator.validator.BestPracticesValidator;
import us.dot.its.jpo.timvalidator.validator.TimJsonValidator;

/**
 * Main service orchestrating the TIM validation pipeline.
 * 
 * Process flow:
 * 1. Convert UPER hex string to XER (XML)
 * 2. Deserialize XER into a typed MessageFrame POJO
 * 3. Perform schema validation
 * 4. Execute best practices checks
 * 5. Return processed validation result
 */
public class TimValidationService {

    private final UperToMessageFrameConverter uperToMessageFrameConverter;
    private final TimJsonValidator schemaValidator;
    private final BestPracticesValidator bestPracticesValidator;

    public TimValidationService() {
        this.uperToMessageFrameConverter = new UperToMessageFrameConverter();
        this.schemaValidator = new TimJsonValidator();
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
        result.setUperInput(uperString);

        try {
            // Step 1: Convert UPER to XER
            String xerFormat = uperToMessageFrameConverter.convertUperToXer(uperString);
            result.setXerFormat(xerFormat);

            // Step 2: Deserialize into POJO
            MessageFrame<?> timMessage = uperToMessageFrameConverter.deserializeToObject(xerFormat);
            result.setTimMessage(timMessage);

            // Step 3: Perform schema validation
            try {
                schemaValidator.validate(timMessage);
                result.addValidationCheck("Schema Validation", true, "Message conforms to J2735 schema");
            } catch (Exception ex) {
                result.addValidationCheck("Schema Validation", false, ex.getMessage());
                throw ex;
            }

            // Step 4: Perform best practices checks
            java.util.List<String> bestPracticesIssues = bestPracticesValidator.validate(timMessage);
            if (bestPracticesIssues.isEmpty()) {
                result.addValidationCheck("Best Practices", true, "All best practices checks passed");
            } else {
                result.addValidationCheck("Best Practices", false, String.join("; ", bestPracticesIssues));
            }

            result.setValid(true);
        } catch (Exception e) {
            if (result.getValidationChecks().isEmpty()) {
                result.addValidationCheck("Validation Pipeline", false, e.getMessage());
            }
            result.setValid(false);
            result.setErrorMessage(e.getMessage());
            throw new ValidationException("TIM validation failed: " + e.getMessage(), e, result);
        }

        return result;
    }
}
