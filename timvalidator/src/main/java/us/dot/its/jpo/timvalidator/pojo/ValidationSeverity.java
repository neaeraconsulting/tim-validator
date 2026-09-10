package us.dot.its.jpo.timvalidator.pojo;

/**
 * Represents the severity level of a validation issue.
 * Possible values are ERROR and WARNING.
 */
public enum ValidationSeverity {
    /**
     * ERRORS indicate a known problem in the message resulting from a violation of the validation rules.
     */
    ERROR,
    
    /**
     * WARNINGS indicate a potential issue or advisory that does not necessarily violate the validation rules but may impact TIM message performance on certain devices.
     * WARNINGS may also be used when available guidance is uncertain or incomplete.
     */
    WARNING
}
