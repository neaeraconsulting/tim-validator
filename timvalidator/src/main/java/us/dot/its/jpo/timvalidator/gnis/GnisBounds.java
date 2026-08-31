package us.dot.its.jpo.timvalidator.gnis;

/** A WGS-84 longitude/latitude query envelope. */
public record GnisBounds(
        double minimumLongitude,
        double maximumLongitude,
        double minimumLatitude,
        double maximumLatitude) {

    public GnisBounds {
        if (!Double.isFinite(minimumLongitude)
                || !Double.isFinite(maximumLongitude)
                || !Double.isFinite(minimumLatitude)
                || !Double.isFinite(maximumLatitude)
                || minimumLongitude < -180.0
                || maximumLongitude > 180.0
                || minimumLatitude < -90.0
                || maximumLatitude > 90.0
                || minimumLongitude > maximumLongitude
                || minimumLatitude > maximumLatitude) {
            throw new IllegalArgumentException("invalid WGS-84 bounds");
        }
    }

    public boolean contains(double longitude, double latitude) {
        return longitude >= minimumLongitude
                && longitude <= maximumLongitude
                && latitude >= minimumLatitude
                && latitude <= maximumLatitude;
    }
}
