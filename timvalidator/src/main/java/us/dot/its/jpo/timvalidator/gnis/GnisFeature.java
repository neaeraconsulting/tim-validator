package us.dot.its.jpo.timvalidator.gnis;

import org.locationtech.jts.geom.Coordinate;

/** A GNIS feature and its official representative location. */
public record GnisFeature(
        int id,
        String name,
        String featureClass,
        Coordinate location) {

    public GnisFeature {
        if (id < 0 || id > 0xFFFFFF) {
            throw new IllegalArgumentException("id must be an unsigned 24-bit value");
        }
        if (location != null) {
            location = location.copy();
        }
    }

    @Override
    public Coordinate location() {
        return location == null ? null : location.copy();
    }
}
