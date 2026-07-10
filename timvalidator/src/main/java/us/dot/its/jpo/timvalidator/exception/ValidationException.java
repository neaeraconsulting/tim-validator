package us.dot.its.jpo.timvalidator.exception;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import us.dot.its.jpo.timvalidator.pojo.ValidationFieldError;
import us.dot.its.jpo.timvalidator.pojo.ValidationResult;

/**
 * Custom exception thrown when TIM message validation fails.
 */
@Getter
public class ValidationException extends Exception {

    private final ValidationResult validationResult;
    private final List<ValidationFieldError> fieldErrors;

    public ValidationException(String message) {
        super(message);
        this.validationResult = null;
        this.fieldErrors = List.of();
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.validationResult = null;
        this.fieldErrors = List.of();
    }

    public ValidationException(Throwable cause) {
        super(cause);
        this.validationResult = null;
        this.fieldErrors = List.of();
    }

    public ValidationException(String message, List<ValidationFieldError> fieldErrors) {
        super(message);
        this.validationResult = null;
        this.fieldErrors = copyFieldErrors(fieldErrors);
    }

    public ValidationException(String message, Throwable cause, ValidationResult validationResult) {
        super(message, cause);
        this.validationResult = validationResult;
        this.fieldErrors = List.of();
    }

    public List<ValidationFieldError> getFieldErrors() {
        return copyFieldErrors(fieldErrors);
    }

    private static List<ValidationFieldError> copyFieldErrors(List<ValidationFieldError> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(fieldErrors);
    }
}
