package us.dot.its.jpo.timvalidator.pojo;

/**
 * Represents a single validation issue encountered during TIM validation.
 * 
 * @param severity the severity level of the validation issue, such as ERROR or WARNING
 * @param checkName the name of the validation check that produced this issue
 * @param message a descriptive message explaining the validation issue
 * @param path the path or location within the data where the issue was found
 */
public record ValidationIssue(
        ValidationSeverity severity,
        String checkName,
        String message,
        String path) {
}
