package us.dot.its.jpo.timvalidator.pojo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;

/**
 * Processed validation result for a TIM message.
 * Provides a comprehensive summary of validation status, individual check results,
 * and the deserialized message for reference.
 */
@Getter
@Setter
public class ValidationResult {

    /**
     * The error message associated with the validation result.
     */
    private String errorMessage;

    /**
     * The original UPER-encoded input for the TIM message.
     */
    private String uperInput;
    /**
     * The original JER-encoded input for the TIM message.
     */
    private String jerInput;

    /**
     * The original XER-encoded input for the TIM message.
     */
    private String xerFormat;

    /**
     * The deserialized TIM message as a POJO.
     */
    private TravelerInformationMessageFrame timMessage;

    /**
     * The timestamp when the validation was performed in UTC.
     */
    private Instant validationTimestamp;

    /**
     * The duration of the validation process in milliseconds.
     */
    private long validationDurationMs;

    /**
     * Indicates whether roadway heading validation is enabled.
     */
    private boolean roadwayHeadingValidationEnabled;

    /**
     * The map of validation check results, keyed by the check name.
     */
    private final Map<String, CheckResult> validationChecks;

    /**
     * The list of validation issues encountered during the validation process.
     */
    private final List<ValidationIssue> issues;

    /**
     * Initializes a new instance of the ValidationResult class with default values.
     */
    public ValidationResult() {
        this.validationTimestamp = Instant.now();
        this.validationChecks = new HashMap<>();
        this.issues = new ArrayList<>();
    }

    /**
     * Adds a validation check result to the overall result.
     * 
     * @param checkName name of the validation check
     * @param passed whether the check passed
     * @param details detailed message about the check result
     */
    public void addValidationCheck(String checkName, boolean passed, String details) {
        validationChecks.put(checkName, new CheckResult(checkName, passed, details));
    }

    /**
     * Adds a validation issue to the overall result.
     *
     * @param issue structured validation issue
     */
    public void addIssue(ValidationIssue issue) {
        if (issue != null) {
            issues.add(issue);
        }
    }

    /**
     * Adds validation issues to the overall result.
     *
     * @param issues structured validation issues
     */
    public void addIssues(List<ValidationIssue> issues) {
        if (issues == null) {
            return;
        }
        issues.forEach(this::addIssue);
    }

    /**
     * Adds an error issue to the overall result.
     * @param checkName name of the validation check that produced the error
     * @param message detailed error message
     * @param path the path within the data structure where the error occurred
     */
    public void addError(String checkName, String message, String path) {
        addIssue(new ValidationIssue(ValidationSeverity.ERROR, checkName, message, path));
    }

    /**
     * Adds a warning issue to the overall result.
     * @param checkName name of the validation check that produced the warning
     * @param message detailed warning message
     * @param path the path within the data structure where the warning occurred
     */
    public void addWarning(String checkName, String message, String path) {
        addIssue(new ValidationIssue(ValidationSeverity.WARNING, checkName, message, path));
    }

    /**
     * Checks if the overall validation result is valid (i.e., contains no errors).
     *
     * @return true if there are no error issues, false otherwise
     */
    public boolean isValid() {
        return getErrors().isEmpty();
    }

    /**
     * Generates a human-readable summary of the validation.
     * @return a human-readable summary of the validation result
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("TIM Validation Result\n");
        summary.append("====================\n");
        summary.append("Overall Status: ").append(isValid() ? "VALID" : "INVALID").append("\n");
        summary.append("Timestamp: ").append(validationTimestamp).append("\n");
        summary.append("Duration: ").append(validationDurationMs).append("ms\n\n");
        summary.append("Roadway heading validation: ")
                .append(roadwayHeadingValidationEnabled ? "ENABLED" : "DISABLED")
                .append("\n\n");

        summary.append("Validation Checks:\n");
        for (CheckResult check : validationChecks.values()) {
            summary.append("  - ").append(check.getName()).append(": ")
                    .append(check.isPassed() ? "PASS" : "FAIL").append("\n");
            if (check.getDetails() != null && !check.getDetails().isEmpty()) {
                summary.append("    Details: ").append(check.getDetails()).append("\n");
            }
        }

        if (!issues.isEmpty()) {
            summary.append("\nIssues:\n");
            for (ValidationIssue issue : issues) {
                summary.append("  - ").append(issue.severity()).append(" [").append(issue.checkName()).append("]");
                if (issue.path() != null && !issue.path().isBlank()) {
                    summary.append(" ").append(issue.path());
                }
                summary.append(": ").append(issue.message()).append("\n");
            }
        }

        if (errorMessage != null && !errorMessage.isEmpty()) {
            summary.append("\nError: ").append(errorMessage).append("\n");
        }

        return summary.toString();
    }

    /**
     * Returns a copy of the validation checks and their results.
     *
     * @return a map of validation check names to their corresponding results
     */
    public Map<String, CheckResult> getValidationChecks() {
        return new HashMap<>(validationChecks);
    }

    /**
     * Returns a copy of the list of validation issues.
     *
     * @return a list of validation issues
     */
    public List<ValidationIssue> getIssues() {
        return new ArrayList<>(issues);
    }

    /**
     * Returns a list of validation issues with severity ERROR.
     *
     * @return a list of error validation issues
     */
    public List<ValidationIssue> getErrors() {
        return issues.stream()
            .filter(issue -> issue.severity() == ValidationSeverity.ERROR)
            .collect(Collectors.toList());
    }

    /**
     * Returns a list of validation issues with severity WARNING.
     *
     * @return a list of warning validation issues
     */
    public List<ValidationIssue> getWarnings() {
        return issues.stream()
            .filter(issue -> issue.severity() == ValidationSeverity.WARNING)
            .collect(Collectors.toList());
    }


    /**
     * Inner class representing a single validation check result.
     */
    @Getter
    @AllArgsConstructor
    public static class CheckResult {
        private final String name;
        private final boolean passed;
        private final String details;
    }
}
