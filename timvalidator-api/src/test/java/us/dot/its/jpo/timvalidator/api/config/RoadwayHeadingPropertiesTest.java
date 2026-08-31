package us.dot.its.jpo.timvalidator.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class RoadwayHeadingPropertiesTest {

    @Test
    void defaults_matchDocumentedDefaults() {
        RoadwayHeadingProperties properties = new RoadwayHeadingProperties();

        assertFalse(properties.isEnabledByDefault());
        assertEquals(RoadwayHeadingProperties.DEFAULT_OVERPASS_URL, properties.getOverpassUrl());
        assertEquals(
            RoadwayHeadingProperties.DEFAULT_OVERPASS_USER_AGENT,
            properties.getOverpassUserAgent());
    }

    @Test
    void setOverpassUrl_overridesDefault() {
        RoadwayHeadingProperties properties = new RoadwayHeadingProperties();
        properties.setOverpassUrl("https://overpass.example/api/interpreter");

        assertEquals("https://overpass.example/api/interpreter", properties.getOverpassUrl());
    }

    @Test
    void setOverpassUserAgent_overridesDefault() {
        RoadwayHeadingProperties properties = new RoadwayHeadingProperties();
        properties.setOverpassUserAgent("my-app/1.0");

        assertEquals("my-app/1.0", properties.getOverpassUserAgent());
    }

}
