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

    /**
     * The validation result associated with this exception, if available.
     */
    private final ValidationResult validationResult;

    /**
     * The list of validation issues associated with this exception.
     */
    private final List<ValidationIssue> issues;

    /**
     * Creates a new Validation exception with the specified message. Does not explicitly set the validation result or issues.
     * @param message the detail message for this exception
     */
    public ValidationException(String message) {
        super(message);
        this.validationResult = null;
        this.issues = List.of();
    }

    /**
     * Creates a new ValidationException with the specified message and cause. Does not explicitly set the validation result or issues.
     * @param message the detail message for this exception
     * @param cause the cause of this exception
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.validationResult = null;
        this.issues = List.of();
    }

    /**
     * Creates a new ValidationException with the specified message and list of validation issues. Does not explicitly set the validation result.
     * @param message the detail message for this exception
     * @param issues the list of validation issues associated with this exception
     */
    public ValidationException(String message, List<ValidationIssue> issues) {
        super(message);
        this.validationResult = null;
        this.issues = copyIssues(issues);
    }

    /**
     * Creates a new ValidationException with the specified cause. Does not explicitly set the Exception message, validation result, or provide a list of issues. 
     * @param cause the cause of this exception
     */
    public ValidationException(Throwable cause) {
        super(cause);
        this.validationResult = null;
        this.issues = List.of();
    }

    /**
     * Creates a new ValidationException with the specified message, cause, and validation result. The list of issues is derived from the validation result's errors.
     * @param message the detail message for this exception
     * @param cause the cause of this exception
     * @param validationResult the validation result associated with this exception
     */
    public ValidationException(String message, Throwable cause, ValidationResult validationResult) {
        super(message, cause);
        this.validationResult = validationResult;
        this.issues = validationResult == null ? List.of() : validationResult.getErrors();
    }

    /**
     * Creates a copy of the provided list of validation issues. If the input list is null, an empty list is returned.
     * This function creates a shallow copy of the list, meaning the list itself is new, but the individual ValidationIssue objects are not cloned.
     * @param issues the list of validation issues to copy
     * @return a new list containing the same validation issues, or an empty list if the input is null
     */
    private static List<ValidationIssue> copyIssues(List<ValidationIssue> issues) {
        if (issues == null) {
            return List.of();
        }
        return new ArrayList<>(issues);
    }
}
