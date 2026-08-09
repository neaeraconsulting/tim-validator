package us.dot.its.jpo.timvalidator.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment policy for optional roadway-backed heading validation. */
@ConfigurationProperties(prefix = "timvalidator.roadway-heading")
public class RoadwayHeadingProperties {

    /** Whether roadway heading validation runs when a request omits the query option. */
    private boolean enabledByDefault;

    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    public void setEnabledByDefault(boolean enabledByDefault) {
        this.enabledByDefault = enabledByDefault;
    }
}
