package us.dot.its.jpo.timvalidator.api.dto;

import java.time.Instant;
import java.util.List;

import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationResult;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;

public record ValidationResponse(
    boolean valid,
    List<ValidationIssueResponse> issues,
    List<ValidationCheckResponse> checks,
    boolean roadwayHeadingValidationEnabled,
    Instant validationTimestamp,
    long validationDurationMs
) {

    public static ValidationResponse from(ValidationResult validationResult) {
        List<ValidationIssueResponse> issues = validationResult.getIssues().stream()
            .map(ValidationIssueResponse::from)
            .toList();
        List<ValidationCheckResponse> checks = validationResult.getValidationChecks().values().stream()
            .map(ValidationCheckResponse::from)
            .toList();

        if (issues.isEmpty()
            && !validationResult.isValid()
            && validationResult.getErrorMessage() != null
            && !validationResult.getErrorMessage().isBlank()) {
            issues = List.of(ValidationIssueResponse.from(new ValidationIssue(
                ValidationSeverity.ERROR,
                "Validation Pipeline",
                validationResult.getErrorMessage(),
                null
            )));
        }

        return new ValidationResponse(
            validationResult.isValid(),
            issues,
            checks,
            validationResult.isRoadwayHeadingValidationEnabled(),
            validationResult.getValidationTimestamp(),
            validationResult.getValidationDurationMs()
        );
    }

    public static ValidationResponse failure(String message) {
        return new ValidationResponse(
            false,
            List.of(new ValidationIssueResponse(ValidationSeverity.ERROR, "Request", message, null)),
            List.of(new ValidationCheckResponse("Request", false, message)),
            false,
            Instant.now(),
            0L
        );
    }
}
