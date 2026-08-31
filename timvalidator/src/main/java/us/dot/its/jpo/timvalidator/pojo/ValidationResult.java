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
 * 
 * Provides a comprehensive summary of validation status, individual check results,
 * and the deserialized message for reference.
 */
@Getter
@Setter
public class ValidationResult {

    private String errorMessage;
    private String uperInput;
    private String jerInput;
    private String xerFormat;
    private TravelerInformationMessageFrame timMessage; // The deserialized POJO
    private Instant validationTimestamp;
    private long validationDurationMs;
    private boolean roadwayHeadingValidationEnabled;

    private final Map<String, CheckResult> validationChecks; // Maps check name to result
    private final List<ValidationIssue> issues;

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

    public void addError(String checkName, String message, String path) {
        addIssue(new ValidationIssue(ValidationSeverity.ERROR, checkName, message, path));
    }

    public void addWarning(String checkName, String message, String path) {
        addIssue(new ValidationIssue(ValidationSeverity.WARNING, checkName, message, path));
    }

    public boolean isValid() {
        return getErrors().isEmpty();
    }

    /**
     * Generates a human-readable summary of the validation.
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

    public Map<String, CheckResult> getValidationChecks() {
        return new HashMap<>(validationChecks);
    }

    public List<ValidationIssue> getIssues() {
        return new ArrayList<>(issues);
    }

    public List<ValidationIssue> getErrors() {
        return issues.stream()
            .filter(issue -> issue.severity() == ValidationSeverity.ERROR)
            .collect(Collectors.toList());
    }

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
