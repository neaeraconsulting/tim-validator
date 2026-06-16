package us.dot.its.jpo.timvalidator.exception;

import us.dot.its.jpo.timvalidator.pojo.ValidationResult;

/**
 * Custom exception thrown when TIM message validation fails.
 */
public class ValidationException extends Exception {

    private final ValidationResult validationResult;

    public ValidationException(String message) {
        super(message);
        this.validationResult = null;
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.validationResult = null;
    }

    public ValidationException(Throwable cause) {
        super(cause);
        this.validationResult = null;
    }

    public ValidationException(String message, Throwable cause, ValidationResult validationResult) {
        super(message, cause);
        this.validationResult = validationResult;
    }

    public ValidationResult getValidationResult() {
        return validationResult;
    }
}
