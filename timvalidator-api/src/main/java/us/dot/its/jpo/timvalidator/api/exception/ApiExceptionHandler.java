package us.dot.its.jpo.timvalidator.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import us.dot.its.jpo.timvalidator.api.dto.ValidationResponse;
import us.dot.its.jpo.timvalidator.exception.ValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ValidationResponse> handleValidationException(ValidationException ex) {
        if (ex.getValidationResult() != null) {
            return ResponseEntity.ok(ValidationResponse.from(ex.getValidationResult()));
        }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ValidationResponse.failure(ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationResponse> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ValidationResponse.failure("Request body must be valid application/json JER"));
    }
}
