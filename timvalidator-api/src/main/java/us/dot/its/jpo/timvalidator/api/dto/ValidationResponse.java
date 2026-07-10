package us.dot.its.jpo.timvalidator.api.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import us.dot.its.jpo.timvalidator.pojo.ValidationResult;

public record ValidationResponse(
    boolean valid,
    List<String> errors,
    List<String> warnings,
    List<ValidationFieldErrorResponse> fieldErrors,
    List<ValidationCheckResponse> checks,
    Instant validationTimestamp,
    long validationDurationMs
) {

    public static ValidationResponse from(ValidationResult validationResult) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<ValidationFieldErrorResponse> fieldErrors = validationResult.getFieldErrors().stream()
            .map(ValidationFieldErrorResponse::from)
            .toList();
        List<ValidationCheckResponse> checks = new ArrayList<>();

        for (ValidationResult.CheckResult checkResult : validationResult.getValidationChecks().values()) {
            checks.add(ValidationCheckResponse.from(checkResult));
            if (!checkResult.isPassed()) {
                addIssue(checkResult, errors, warnings);
            }
        }

        if (!validationResult.isValid()
            && validationResult.getErrorMessage() != null
            && !validationResult.getErrorMessage().isBlank()
            && errors.isEmpty()) {
            errors.add(validationResult.getErrorMessage());
        }

        return new ValidationResponse(
            validationResult.isValid(),
            errors,
            warnings,
            fieldErrors,
            checks,
            validationResult.getValidationTimestamp(),
            validationResult.getValidationDurationMs()
        );
    }

    public static ValidationResponse failure(String message) {
        List<String> errors = new ArrayList<>();
        errors.add(message);

        return new ValidationResponse(
            false,
            errors,
            List.of(),
            List.of(),
            List.of(new ValidationCheckResponse("Request", false, message)),
            Instant.now(),
            0L
        );
    }

    private static void addIssue(
        ValidationResult.CheckResult checkResult,
        List<String> errors,
        List<String> warnings
    ) {
        String issue = checkResult.getName() + ": " + checkResult.getDetails();
        if ("Best Practices".equals(checkResult.getName())) {
            warnings.add(issue);
        } else {
            errors.add(issue);
        }
    }
}
