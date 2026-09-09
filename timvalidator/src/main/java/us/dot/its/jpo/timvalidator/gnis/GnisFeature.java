package us.dot.its.jpo.timvalidator.gnis;

import org.locationtech.jts.geom.Coordinate;

/** A 
 * GNIS feature and its official representative location. 
 * @param id the unique identifier of the GNIS feature
 * @param name the name of the GNIS feature
 * @param featureClass the featureClass name of the GNIS feature. Class name definitions are available here: https://www.usgs.gov/us-board-on-geographic-names/gnis-domestic-names-feature-classes
 * @param location the official representative location of the GNIS feature
 * */
public record GnisFeature(
        int id,
        String name,
        String featureClass,
        Coordinate location) {


    /**
     * Constructs a new GnisFeature record, ensuring the location is copied to maintain immutability. 
     * @throws IllegalArgumentException if the id is not an unsigned 24-bit value
     */
    public GnisFeature {
        if (id < 0 || id > 0xFFFFFF) {
            throw new IllegalArgumentException("id must be an unsigned 24-bit value");
        }
        if (location != null) {
            location = location.copy();
        }
    }

    /**
     * Returns a copy of the official representative location of the GNIS feature.
     * @return a copy of the location, or null if the location is not set
     */
    @Override
    public Coordinate location() {
        return location == null ? null : location.copy();
    }
}
