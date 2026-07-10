package us.dot.its.jpo.timvalidator.api.dto;

import us.dot.its.jpo.timvalidator.pojo.ValidationResult;

public record ValidationCheckResponse(
    String name,
    boolean passed,
    String details
) {

    public static ValidationCheckResponse from(ValidationResult.CheckResult checkResult) {
        return new ValidationCheckResponse(
            checkResult.getName(),
            checkResult.isPassed(),
            checkResult.getDetails()
        );
    }
}
