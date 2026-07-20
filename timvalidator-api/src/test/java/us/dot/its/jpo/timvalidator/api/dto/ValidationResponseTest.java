package us.dot.its.jpo.timvalidator.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import us.dot.its.jpo.timvalidator.pojo.ValidationResult;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;

class ValidationResponseTest {

    @Test
    void from_failedNonBestPracticeCheck_mapsToErrors() {
        ValidationResult validationResult = new ValidationResult();
        validationResult.addValidationCheck("Schema Validation", false, "required property missing");
        validationResult.addError("Schema Validation", "required property missing", "/tim");

        ValidationResponse response = ValidationResponse.from(validationResult);

        assertFalse(response.valid());
        assertEquals(1, response.issues().size());
        assertEquals(ValidationSeverity.ERROR, response.issues().getFirst().severity());
        assertEquals("Schema Validation", response.issues().getFirst().checkName());
        assertEquals("/tim", response.issues().getFirst().path());
        assertEquals("required property missing", response.issues().getFirst().message());
    }

    @Test
    void from_failedBestPracticeCheck_mapsToWarnings() {
        ValidationResult validationResult = new ValidationResult();
        validationResult.addValidationCheck("Best Practices", false, "advisory issue found");
        validationResult.addWarning("Best Practices", "advisory issue found", null);

        ValidationResponse response = ValidationResponse.from(validationResult);

        assertTrue(response.valid());
        assertEquals(1, response.issues().size());
        assertEquals(ValidationSeverity.WARNING, response.issues().getFirst().severity());
        assertEquals("Best Practices", response.issues().getFirst().checkName());
        assertEquals("advisory issue found", response.issues().getFirst().message());
        assertEquals(1, response.checks().size());
        assertEquals("Best Practices", response.checks().getFirst().name());
        assertFalse(response.checks().getFirst().passed());
    }
}
