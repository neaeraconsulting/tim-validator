package us.dot.its.jpo.timvalidator.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import us.dot.its.jpo.timvalidator.pojo.ValidationFieldError;
import us.dot.its.jpo.timvalidator.pojo.ValidationResult;

class ValidationResponseTest {

    @Test
    void from_failedNonBestPracticeCheck_mapsToErrors() {
        ValidationResult validationResult = new ValidationResult();
        validationResult.setValid(false);
        validationResult.addValidationCheck("Schema Validation", false, "required property missing");
        validationResult.addFieldErrors(List.of(
            new ValidationFieldError("$.tim", "required property missing")
        ));

        ValidationResponse response = ValidationResponse.from(validationResult);

        assertFalse(response.valid());
        assertEquals(List.of("Schema Validation: required property missing"), response.errors());
        assertTrue(response.warnings().isEmpty());
        assertEquals(1, response.fieldErrors().size());
        assertEquals("$.tim", response.fieldErrors().getFirst().path());
        assertEquals("required property missing", response.fieldErrors().getFirst().message());
    }

    @Test
    void from_failedBestPracticeCheck_mapsToWarnings() {
        ValidationResult validationResult = new ValidationResult();
        validationResult.setValid(true);
        validationResult.addValidationCheck("Best Practices", false, "advisory issue found");

        ValidationResponse response = ValidationResponse.from(validationResult);

        assertTrue(response.valid());
        assertTrue(response.errors().isEmpty());
        assertEquals(List.of("Best Practices: advisory issue found"), response.warnings());
        assertEquals(1, response.checks().size());
        assertEquals("Best Practices", response.checks().getFirst().name());
        assertFalse(response.checks().getFirst().passed());
    }
}
