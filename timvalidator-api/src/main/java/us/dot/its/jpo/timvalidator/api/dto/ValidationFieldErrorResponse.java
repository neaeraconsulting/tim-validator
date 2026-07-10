package us.dot.its.jpo.timvalidator.api.dto;

import us.dot.its.jpo.timvalidator.pojo.ValidationFieldError;

public record ValidationFieldErrorResponse(
    String path,
    String message
) {

    public static ValidationFieldErrorResponse from(ValidationFieldError fieldError) {
        return new ValidationFieldErrorResponse(
            fieldError.getPath(),
            fieldError.getMessage()
        );
    }
}
