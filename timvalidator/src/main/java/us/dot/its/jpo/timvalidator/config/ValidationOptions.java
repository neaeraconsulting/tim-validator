package us.dot.its.jpo.timvalidator.config;

/**
 * Options that control checks performed for a single TIM validation.
 *
 * <p>The default options are deterministic and network-free. Roadway heading
 * validation must be explicitly enabled. The standard service uses Overpass,
 * while callers may inject a different {@code RoadGeometryProvider}.
 *
 * @param roadwayHeadingEnabled whether directional heading slices should be
 *        checked against external roadway geometry
 */
public record ValidationOptions(boolean roadwayHeadingEnabled) {

    private static final ValidationOptions NETWORK_FREE = new ValidationOptions(false);
    private static final ValidationOptions WITH_ROADWAY_HEADING = new ValidationOptions(true);

    /** Returns the default, network-free validation options. 
     * @return the default, network-free validation options. roadwayHeading validation is disabled
     */
    public static ValidationOptions defaults() {
        return NETWORK_FREE;
    }

    /** Returns ValidationOptions object with roadway headings disabled. 
     * @return validation options with roadway headings disabled
    */
    public static ValidationOptions networkFree() {
        return NETWORK_FREE;
    }

    /** Returns ValidationOptions object with roadway headings enabled. 
     * @return validation options with roadway headings enabled
     */
    public static ValidationOptions withRoadwayHeading() {
        return WITH_ROADWAY_HEADING;
    }
}
