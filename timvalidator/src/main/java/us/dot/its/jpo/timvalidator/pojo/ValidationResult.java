package us.dot.its.jpo.timvalidator.pojo;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

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

    private boolean valid;
    private String errorMessage;
    private String uperInput;
    private String xerFormat;
    private TravelerInformationMessageFrame timMessage; // The deserialized POJO
    private Instant validationTimestamp;
    private long validationDurationMs;
    
    private final Map<String, CheckResult> validationChecks; // Maps check name to result

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

    public Map<String, CheckResult> getValidationChecks() {
        return new HashMap<>(validationChecks);
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
