package us.dot.its.jpo.timvalidator.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.dot.its.jpo.timvalidator.converter.UperToMessageFrameConverter;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.pojo.ValidationResult;
import us.dot.its.jpo.timvalidator.validator.BestPracticesValidator;
import us.dot.its.jpo.timvalidator.validator.TimJsonValidator;

/**
 * Basic test suite to verify Maven build and project structure.
 */
public class TimValidationServiceTest {

    private TimValidationService validationService;

    @BeforeEach
    public void setUp() {
        validationService = new TimValidationService();
    }

    @Test
    public void testServiceInstantiation() {
        assertNotNull(validationService, "Service should be instantiated successfully");
    }

    @Test
    public void testValidationResultCreation() {
        ValidationResult result = new ValidationResult();
        assertNotNull(result, "ValidationResult should be created");
        assertTrue(result.isValid(), "New ValidationResult should be valid by default");
    }

    @Test
    public void testValidationCheckAddition() {
        ValidationResult result = new ValidationResult();
        result.addValidationCheck("Test Check", true, "Test passed");
        
        assertNotNull(result.getValidationChecks(), "Validation checks should not be null");
        assertTrue(result.getValidationChecks().containsKey("Test Check"), 
                   "Test check should be added to results");
    }

    @Test
    public void testValidationResultSummary() {
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        result.addValidationCheck("Schema Validation", true, "Schema check passed");
        result.addValidationCheck("Best Practices", true, "All best practices passed");
        
        String summary = result.getSummary();
        assertNotNull(summary, "Summary should be generated");
        assertTrue(summary.contains("VALID"), "Summary should contain validation status");
        assertTrue(summary.contains("Schema Validation"), "Summary should contain check names");
    }

    @Test
    public void testValidationException() {
        ValidationException ex = assertThrows(ValidationException.class, () -> {
            throw new ValidationException("Test exception");
        }, "ValidationException should be throwable");
        assertTrue(ex.getMessage().contains("Test exception"));
    }

    @Test
    public void testUperToMessageFrameConverterInstantiation() {
        UperToMessageFrameConverter converter = new UperToMessageFrameConverter();
        assertNotNull(converter, "UperToMessageFrameConverter should be instantiated");
    }

    @Test
    public void testTimJsonValidatorInstantiation() {
        TimJsonValidator validator = new TimJsonValidator();
        assertNotNull(validator, "TimJsonValidator should be instantiated");
    }

    @Test
    public void testBestPracticesValidatorInstantiation() {
        BestPracticesValidator validator = new BestPracticesValidator();
        assertNotNull(validator, "BestPracticesValidator should be instantiated");
    }

    @Test
    public void testBestPracticesValidatorNullHandling() {
        BestPracticesValidator validator = new BestPracticesValidator();
        var issues = validator.validate(null);
        
        assertNotNull(issues, "Issues list should not be null");
        assertFalse(issues.isEmpty(), "Should report issues for null message");
    }
}
