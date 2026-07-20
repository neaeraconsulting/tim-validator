package us.dot.its.jpo.timvalidator.api.dto;

import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;

public record ValidationIssueResponse(
    ValidationSeverity severity,
    String checkName,
    String message,
    String path
) {

    public static ValidationIssueResponse from(ValidationIssue issue) {
        return new ValidationIssueResponse(
            issue.severity(),
            issue.checkName(),
            issue.message(),
            issue.path()
        );
    }
}
