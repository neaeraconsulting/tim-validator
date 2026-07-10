package us.dot.its.jpo.timvalidator.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Field-specific validation error for UI highlighting.
 */
@Getter
@AllArgsConstructor
public class ValidationFieldError {

    private final String path;
    private final String message;
}
