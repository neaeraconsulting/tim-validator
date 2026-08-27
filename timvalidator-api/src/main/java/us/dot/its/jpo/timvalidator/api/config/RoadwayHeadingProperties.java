package us.dot.its.jpo.timvalidator.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment policy for optional roadway-backed heading validation.
 *
 * <p>The library itself has no default Overpass endpoint or User-Agent (it must never
 * silently enroll every consumer of the published library into hitting shared Overpass
 * infrastructure under one identity). This application, being one specific deployment, may
 * reasonably default to the public Overpass instance and a value identifying it — but see
 * the usage guidelines at
 * <a href="https://wiki.openstreetmap.org/wiki/Overpass_API#Rules_of_usage">
 * wiki.openstreetmap.org/wiki/Overpass_API#Rules_of_usage</a> and override both for any
 * production or commercial deployment.
 */
@ConfigurationProperties(prefix = "timvalidator.roadway-heading")
public class RoadwayHeadingProperties {

    /** The public Overpass instance used unless overridden by deployment configuration. */
    public static final String DEFAULT_OVERPASS_URL = "https://overpass-api.de/api/interpreter";

    /** A generic User-Agent; deployers should override this with a value unique to them. */
    public static final String DEFAULT_OVERPASS_USER_AGENT = "timvalidator-api/1.0";

    /** Whether roadway heading validation runs when a request omits the query option. */
    private boolean enabledByDefault;

    /** The Overpass API endpoint URL queried for roadway geometry. */
    private String overpassUrl = DEFAULT_OVERPASS_URL;

    /** The User-Agent sent with Overpass requests; should uniquely identify this deployment. */
    private String overpassUserAgent = DEFAULT_OVERPASS_USER_AGENT;

    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    public void setEnabledByDefault(boolean enabledByDefault) {
        this.enabledByDefault = enabledByDefault;
    }

    public String getOverpassUrl() {
        return overpassUrl;
    }

    public void setOverpassUrl(String overpassUrl) {
        this.overpassUrl = overpassUrl;
    }

    public String getOverpassUserAgent() {
        return overpassUserAgent;
    }

    public void setOverpassUserAgent(String overpassUserAgent) {
        this.overpassUserAgent = overpassUserAgent;
    }
}
