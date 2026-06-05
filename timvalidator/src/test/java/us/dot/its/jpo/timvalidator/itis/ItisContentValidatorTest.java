package us.dot.its.jpo.timvalidator.itis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.dot.its.jpo.timvalidator.exception.ValidationException;

public class ItisContentValidatorTest {

    // Uses the production schema from src/main/resources so tests cover the real catalog.
    private ItisContentValidator validator;

    @BeforeEach
    public void setUp() throws Exception {
        validator = new ItisContentValidator();
    }

    @Test
    public void validate_acceptsSpeedLimitPatternAndMessageFields() throws Exception {
        assertDoesNotThrow(() -> validator.validate("""
            {
              "itis": [6937, 55],
              "priority": 4,
              "allowIndefinite": false,
              "geofence": "path"
            }
            """));
    }

    @Test
    public void validate_acceptsClosedToTrafficPattern() throws Exception {
        assertDoesNotThrow(() -> validator.validate("""
            {
              "itis": [769, 9478, 7747],
              "priority": 4,
              "allowIndefinite": false,
              "geofence": "path"
            }
            """));
    }

    @Test
    public void validate_acceptsGrossWeightLimitPattern() throws Exception {
        assertDoesNotThrow(() -> validator.validate("""
            {
              "itis": [2577, 12000, 8739],
              "priority": 4,
              "allowIndefinite": false,
              "geofence": "path"
            }
            """));
    }

    @Test
    public void validate_acceptsEnumTokenPositionForMphAndKph() throws Exception {
        assertDoesNotThrow(() -> validator.validate("""
            {
              "itis": [268, 12302, 8720, 13569],
              "priority": 4,
              "allowIndefinite": false,
              "geofence": "path"
            }
            """));

        assertDoesNotThrow(() -> validator.validate("""
            {
              "itis": [268, 12302, 8721, 13569],
              "priority": 4,
              "allowIndefinite": false,
              "geofence": "path"
            }
            """));
    }

    @Test
    public void validate_rejectsCodeOutsideEnumTokenPosition() throws Exception {
        ValidationException ex = assertThrows(ValidationException.class, () -> validator.validate("""
            {
              "itis": [268, 12302, 9999, 13569],
              "priority": 4,
              "allowIndefinite": false,
              "geofence": "path"
            }
            """));

        assertTrue(ex.getMessage().contains("$.itis"));
    }

    @Test
    public void validate_rejectsSpeedLimitOutsideSchemaRange() throws Exception {
        // 100 exceeds the SPEED_LIMIT definition's maximum, so no oneOf pattern should match.
        ValidationException ex = assertThrows(ValidationException.class, () -> validator.validate("""
            {
              "itis": [6937, 100],
              "priority": 4,
              "allowIndefinite": false,
              "geofence": "path"
            }
            """));

        assertTrue(ex.getMessage().contains("$.itis"));
    }
}
