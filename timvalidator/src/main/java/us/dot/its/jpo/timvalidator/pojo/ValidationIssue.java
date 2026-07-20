package us.dot.its.jpo.timvalidator.pojo;

public record ValidationIssue(
        ValidationSeverity severity,
        String checkName,
        String message,
        String path) {
}
