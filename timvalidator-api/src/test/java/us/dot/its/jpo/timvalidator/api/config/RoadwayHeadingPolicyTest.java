package us.dot.its.jpo.timvalidator.api.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


class RoadwayHeadingPolicyTest {

    @Test
    void resolve_omittedRequest_isNetworkFreeByDefault() {
        RoadwayHeadingPolicy policy = new RoadwayHeadingPolicy(new RoadwayHeadingProperties());

        assertFalse(policy.resolve(null).roadwayHeadingEnabled());
    }

    @Test
    void resolve_requestCanExplicitlyEnableAndDisableCheck() {
        RoadwayHeadingProperties properties = new RoadwayHeadingProperties();
        properties.setEnabledByDefault(true);
        RoadwayHeadingPolicy policy = new RoadwayHeadingPolicy(properties);

        assertTrue(policy.resolve(null).roadwayHeadingEnabled());
        assertTrue(policy.resolve(true).roadwayHeadingEnabled());
        assertFalse(policy.resolve(false).roadwayHeadingEnabled());
    }

}
