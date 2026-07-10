package us.dot.its.jpo.timvalidator.service;

import java.util.List;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.timvalidator.converter.JerToMessageFrameConverter;
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
 * 2. Deserialize XER into a typed TravelerInformationMessageFrame POJO
 * 3. Perform schema validation
 * 4. Execute best practices checks
 * 5. Return processed validation result
 */
public class TimValidationService {

    private final UperToMessageFrameConverter uperToMessageFrameConverter;
    private final JerToMessageFrameConverter jerToMessageFrameConverter;
    private final TimJsonValidator schemaValidator;
    private final BestPracticesValidator bestPracticesValidator;

    public TimValidationService() {
        this.uperToMessageFrameConverter = new UperToMessageFrameConverter();
        this.jerToMessageFrameConverter = new JerToMessageFrameConverter();
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
            TravelerInformationMessageFrame timMessage = uperToMessageFrameConverter.deserialize(xerFormat);
            result.setTimMessage(timMessage);

            return validateTimMessage(result, timMessage);
        } catch (Exception e) {
            throw buildValidationException(result, e);
        }
    }

    /**
     * Validates a TIM message from JER/JSON format.
     *
     * @param jerString the JER/JSON encoded TIM message
     * @return ValidationResult containing validation status and details
     * @throws ValidationException if validation fails critically
     */
    public ValidationResult validateTimJer(String jerString) throws ValidationException {
        ValidationResult result = new ValidationResult();
        result.setJerInput(jerString);

        try {
            // Step 1: Deserialize into POJO
            TravelerInformationMessageFrame timMessage = jerToMessageFrameConverter.deserialize(jerString);
            result.setTimMessage(timMessage);

            return validateTimMessage(result, timMessage);
        } catch (Exception e) {
            throw buildValidationException(result, e);
        }
    }

    private ValidationResult validateTimMessage(
        ValidationResult result,
        TravelerInformationMessageFrame timMessage
    ) throws ValidationException {
        try {
            schemaValidator.validate(timMessage);
            result.addValidationCheck("Schema Validation", true, "Message conforms to J2735 schema");
        } catch (ValidationException ex) {
            result.addValidationCheck("Schema Validation", false, ex.getMessage());
            result.addFieldErrors(ex.getFieldErrors());
            throw ex;
        }

        List<String> bestPracticesIssues = bestPracticesValidator.validate(timMessage);
        if (bestPracticesIssues.isEmpty()) {
            result.addValidationCheck("Best Practices", true, "All best practices checks passed");
        } else {
            result.addValidationCheck("Best Practices", false, String.join("; ", bestPracticesIssues));
        }

        result.setValid(true);
        return result;
    }

    private ValidationException buildValidationException(ValidationResult result, Exception e) {
        if (result.getValidationChecks().isEmpty()) {
            result.addValidationCheck("Validation Pipeline", false, e.getMessage());
        }
        result.setValid(false);
        result.setErrorMessage(e.getMessage());
        return new ValidationException("TIM validation failed: " + e.getMessage(), e, result);
    }
}
