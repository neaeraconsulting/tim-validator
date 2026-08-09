package us.dot.its.jpo.timvalidator.api.config;

import us.dot.its.jpo.timvalidator.config.ValidationOptions;

/** Resolves a request option against the API deployment policy. */
public final class RoadwayHeadingPolicy {

    private final boolean enabledByDefault;

    public RoadwayHeadingPolicy(RoadwayHeadingProperties properties) {
        this.enabledByDefault = properties.isEnabledByDefault();
    }

    /** Returns network-free or roadway-backed options for one API request. */
    public ValidationOptions resolve(Boolean requested) {
        boolean enabled = requested != null ? requested : enabledByDefault;
        return enabled
                ? ValidationOptions.withRoadwayHeading()
                : ValidationOptions.networkFree();
    }
}
