package us.dot.its.jpo.timvalidator.exception;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationResult;

/**
 * Custom exception thrown when TIM message validation fails.
 */
@Getter
public class ValidationException extends Exception {

    private final ValidationResult validationResult;
    private final List<ValidationIssue> issues;

    public ValidationException(String message) {
        super(message);
        this.validationResult = null;
        this.issues = List.of();
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.validationResult = null;
        this.issues = List.of();
    }

    public ValidationException(String message, List<ValidationIssue> issues) {
        super(message);
        this.validationResult = null;
        this.issues = copyIssues(issues);
    }

    public ValidationException(Throwable cause) {
        super(cause);
        this.validationResult = null;
        this.issues = List.of();
    }

    public ValidationException(String message, Throwable cause, ValidationResult validationResult) {
        super(message, cause);
        this.validationResult = validationResult;
        this.issues = validationResult == null ? List.of() : validationResult.getErrors();
    }

    private static List<ValidationIssue> copyIssues(List<ValidationIssue> issues) {
        if (issues == null) {
            return List.of();
        }
        return new ArrayList<>(issues);
    }
}
