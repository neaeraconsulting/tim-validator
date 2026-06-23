package us.dot.its.jpo.timvalidator.pojo;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Processed validation result for a TIM message.
 * 
 * Provides a comprehensive summary of validation status, individual check results,
 * and the deserialized message for reference.
 */
public class ValidationResult {

    private boolean valid;
    private String errorMessage;
    private String uperInput;
    private String xerFormat;
    private Object timMessage; // The deserialized POJO
    private Instant validationTimestamp;
    private long validationDurationMs;

    private Map<String, CheckResult> validationChecks; // Maps check name to result

    public ValidationResult() {
        this.valid = true;
        this.validationTimestamp = Instant.now();
        this.validationChecks = new HashMap<>();
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
     * Generates a human-readable summary of the validation.
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("TIM Validation Result\n");
        summary.append("====================\n");
        summary.append("Overall Status: ").append(valid ? "VALID" : "INVALID").append("\n");
        summary.append("Timestamp: ").append(validationTimestamp).append("\n");
        summary.append("Duration: ").append(validationDurationMs).append("ms\n\n");

        summary.append("Validation Checks:\n");
        for (CheckResult check : validationChecks.values()) {
            summary.append("  - ").append(check.getName()).append(": ")
                    .append(check.isPassed() ? "PASS" : "FAIL").append("\n");
            if (check.getDetails() != null && !check.getDetails().isEmpty()) {
                summary.append("    Details: ").append(check.getDetails()).append("\n");
            }
        }

        if (errorMessage != null && !errorMessage.isEmpty()) {
            summary.append("\nError: ").append(errorMessage).append("\n");
        }

        return summary.toString();
    }

    // Getters and setters
    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getUperInput() {
        return uperInput;
    }

    public void setUperInput(String uperInput) {
        this.uperInput = uperInput;
    }

    public String getXerFormat() {
        return xerFormat;
    }

    public void setXerFormat(String xerFormat) {
        this.xerFormat = xerFormat;
    }

    public Object getTimMessage() {
        return timMessage;
    }

    public void setTimMessage(Object timMessage) {
        this.timMessage = timMessage;
    }

    public Instant getValidationTimestamp() {
        return validationTimestamp;
    }

    public void setValidationTimestamp(Instant validationTimestamp) {
        this.validationTimestamp = validationTimestamp;
    }

    public long getValidationDurationMs() {
        return validationDurationMs;
    }

    public void setValidationDurationMs(long validationDurationMs) {
        this.validationDurationMs = validationDurationMs;
    }

    public Map<String, CheckResult> getValidationChecks() {
        return new HashMap<>(validationChecks);
    }

    /**
     * Inner class representing a single validation check result.
     */
    public static class CheckResult {
        private final String name;
        private final boolean passed;
        private final String details;

        public CheckResult(String name, boolean passed, String details) {
            this.name = name;
            this.passed = passed;
            this.details = details;
        }

        public String getName() {
            return name;
        }

        public boolean isPassed() {
            return passed;
        }

        public String getDetails() {
            return details;
        }
    }
}
