package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.proj4j.geodesic.Geodesic;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Circle;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrameList;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.timvalidator.gnis.GnisBounds;
import us.dot.its.jpo.timvalidator.validator.OffsetPathDecoder.DecodeResult;

/**
 * A WGS-84 envelope derived from all usable path and circle geometry in a TIM.
 *
 * <p>Longitudes may temporarily use the range {@code 0..360} when that produces
 * the smaller envelope for geometry crossing the antimeridian. Methods that expose
 * query envelopes normalize the result back to {@code -180..180}.</p>
 *
 * @param minimumLongitude western edge in decimal degrees
 * @param maximumLongitude eastern edge in decimal degrees
 * @param minimumLatitude southern edge in decimal degrees
 * @param maximumLatitude northern edge in decimal degrees
 */
record TimGeographicBounds(
        double minimumLongitude,
        double maximumLongitude,
        double minimumLatitude,
        double maximumLatitude) {

    private static final double METERS_PER_LATITUDE_DEGREE = 111_320.0;

    /**
     * Builds one envelope around every decodable region in the TIM.
     *
     * @param tim traveler information containing the regions to inspect
     * @return combined bounds, or empty when no usable region coordinates exist
     */
    static Optional<TimGeographicBounds> from(TravelerInformation tim) {
        if (tim == null) {
            return Optional.empty();
        }
        TravelerDataFrameList dataFrames = tim.getDataFrames();
        if (dataFrames == null) {
            return Optional.empty();
        }

        // A TIM can describe one deployment area with regions in multiple data frames.
        List<Coordinate> coordinates = new ArrayList<>();
        for (TravelerDataFrame dataFrame : dataFrames) {
            if (dataFrame == null || dataFrame.getRegions() == null) {
                continue;
            }
            for (GeographicalPath region : dataFrame.getRegions()) {
                addRegionCoordinates(region, coordinates);
            }
        }
        return fromCoordinates(coordinates);
    }

    /**
     * Adds the coordinates represented by one path or circle region.
     *
     * @param region region whose geometry should contribute to the TIM bounds
     * @param coordinates destination collection of WGS-84 coordinates
     */
    private static void addRegionCoordinates(
            GeographicalPath region,
            List<Coordinate> coordinates) {
        if (region == null || region.getDescription() == null) {
            return;
        }
        if (region.getDescription().getGeometry() != null) {
            addCircleCoordinates(
                    region.getDescription().getGeometry().getCircle(),
                    coordinates);
        }

        DecodeResult decoded = OffsetPathDecoder.decode(region);
        if (!decoded.decoded()) {
            return;
        }

        // Path nodes are anchor-relative centimeters; the decoder performs the
        // required reprojection without treating the anchor itself as a bounds point.
        OffsetPathDecoder.wgs84Coordinates(region, decoded.path().nodes())
                .ifPresent(coordinates::addAll);
    }

    /**
     * Adds a circle's center and four cardinal edge points to the coordinate set.
     *
     * @param circle circle geometry to convert to WGS-84 extent points
     * @param coordinates destination collection of WGS-84 coordinates
     */
    private static void addCircleCoordinates(
            Circle circle,
            List<Coordinate> coordinates) {
        if (circle == null) {
            return;
        }
        Optional<Coordinate> center = OffsetPathDecoder.wgs84Coordinate(circle.getCenter());
        OptionalDouble radius = CircleGeometryUtils.radiusMeters(circle);
        if (center.isEmpty() || radius.isEmpty()) {
            return;
        }

        Coordinate origin = center.orElseThrow();
        coordinates.add(origin);

        // North, east, south, and west destinations provide a lightweight
        // axis-aligned envelope approximation; elevation is irrelevant here.
        for (double bearing : new double[] {0.0, 90.0, 180.0, 270.0}) {
            var destination = Geodesic.WGS84.Direct(
                    origin.getY(),
                    origin.getX(),
                    bearing,
                    radius.orElseThrow());
            coordinates.add(new CoordinateXY(destination.lon2, destination.lat2));
        }
    }

    /**
     * Finds the smallest longitude representation and latitude range containing
     * the supplied valid WGS-84 coordinates.
     *
     * @param coordinates candidate coordinates in decimal degrees
     * @return bounds around the valid coordinates, or empty if none are usable
     */
    private static Optional<TimGeographicBounds> fromCoordinates(
            List<Coordinate> coordinates) {
        List<Coordinate> valid = coordinates.stream()
                .filter(coordinate -> coordinate != null
                        && coordinate.isValid()
                        && coordinate.getX() >= -180.0
                        && coordinate.getX() <= 180.0
                        && coordinate.getY() >= -90.0
                        && coordinate.getY() <= 90.0)
                .toList();
        if (valid.isEmpty()) {
            return Optional.empty();
        }

        double minimumLatitude = valid.stream().mapToDouble(Coordinate::getY).min().orElseThrow();
        double maximumLatitude = valid.stream().mapToDouble(Coordinate::getY).max().orElseThrow();
        double standardMinimum = valid.stream().mapToDouble(Coordinate::getX).min().orElseThrow();
        double standardMaximum = valid.stream().mapToDouble(Coordinate::getX).max().orElseThrow();
        double shiftedMinimum = valid.stream()
                .mapToDouble(coordinate -> shiftLongitude(coordinate.getX()))
                .min()
                .orElseThrow();
        double shiftedMaximum = valid.stream()
                .mapToDouble(coordinate -> shiftLongitude(coordinate.getX()))
                .max()
                .orElseThrow();

        // Comparing ordinary -180..180 longitudes with a shifted 0..360 range
        // prevents a narrow antimeridian crossing from appearing nearly global.
        if (shiftedMaximum - shiftedMinimum < standardMaximum - standardMinimum) {
            return Optional.of(new TimGeographicBounds(
                    shiftedMinimum,
                    shiftedMaximum,
                    minimumLatitude,
                    maximumLatitude));
        }
        return Optional.of(new TimGeographicBounds(
                standardMinimum,
                standardMaximum,
                minimumLatitude,
                maximumLatitude));
    }

    /**
     * Measures the geodesic distance between opposite corners of the envelope.
     *
     * @return corner-to-corner distance in meters
     */
    double diagonalMeters() {
        return Geodesic.WGS84.Inverse(
                minimumLatitude,
                normalizeLongitude(minimumLongitude),
                maximumLatitude,
                normalizeLongitude(maximumLongitude)).s12;
    }

    /**
     * Expands the envelope by approximately the requested ground distance.
     *
     * <p>The result contains two query envelopes when expansion crosses the
     * antimeridian, allowing ordinary GeoPackage RTree comparisons.</p>
     *
     * @param bufferMeters outward expansion applied to every edge
     * @return one or two normalized WGS-84 query envelopes
     */
    List<GnisBounds> expanded(double bufferMeters) {
        // One degree of latitude has sufficiently stable length for this
        // best-practice search-envelope approximation.
        double latitudePadding = bufferMeters / METERS_PER_LATITUDE_DEGREE;
        double paddedMinimumLatitude = Math.max(-90.0, minimumLatitude - latitudePadding);
        double paddedMaximumLatitude = Math.min(90.0, maximumLatitude + latitudePadding);
        double greatestAbsoluteLatitude = Math.max(
                Math.abs(paddedMinimumLatitude),
                Math.abs(paddedMaximumLatitude));

        // Longitude degrees narrow toward the poles. Using the greatest absolute
        // latitude makes the padding conservative across the entire envelope.
        double longitudeMetersPerDegree = METERS_PER_LATITUDE_DEGREE
                * Math.cos(Math.toRadians(greatestAbsoluteLatitude));
        double longitudePadding = longitudeMetersPerDegree < 1.0
                ? 180.0
                : Math.min(180.0, bufferMeters / longitudeMetersPerDegree);
        return normalizedBounds(
                minimumLongitude - longitudePadding,
                maximumLongitude + longitudePadding,
                paddedMinimumLatitude,
                paddedMaximumLatitude);
    }

    /**
     * Measures the shortest approximate geodesic distance from a point to the envelope.
     *
     * @param point WGS-84 point with longitude as X and latitude as Y
     * @return distance in meters, zero inside the bounds, or positive infinity for
     *         an unusable point
     */
    double distanceMeters(Coordinate point) {
        if (point == null || !point.isValid()) {
            return Double.POSITIVE_INFINITY;
        }
        return normalizedBounds(
                minimumLongitude,
                maximumLongitude,
                minimumLatitude,
                maximumLatitude).stream()
                .mapToDouble(bounds -> distanceMeters(point, bounds))
                .min()
                .orElse(Double.POSITIVE_INFINITY);
    }

    /**
     * Clamps a point to the nearest location on one normalized envelope and measures
     * the WGS-84 geodesic distance to that location.
     */
    private static double distanceMeters(Coordinate point, GnisBounds bounds) {
        // Clamping leaves an interior point unchanged, which naturally produces zero.
        double longitude = Math.max(
                bounds.minimumLongitude(),
                Math.min(bounds.maximumLongitude(), point.getX()));
        double latitude = Math.max(
                bounds.minimumLatitude(),
                Math.min(bounds.maximumLatitude(), point.getY()));
        return Geodesic.WGS84.Inverse(
                point.getY(), point.getX(), latitude, longitude).s12;
    }

    /**
     * Converts an internal longitude interval into valid GeoPackage query bounds,
     * splitting it at the antimeridian when necessary.
     */
    private static List<GnisBounds> normalizedBounds(
            double minimumLongitude,
            double maximumLongitude,
            double minimumLatitude,
            double maximumLatitude) {
        // An interval covering the globe needs no antimeridian split.
        if (maximumLongitude - minimumLongitude >= 360.0) {
            return List.of(new GnisBounds(
                    -180.0, 180.0, minimumLatitude, maximumLatitude));
        }
        while (minimumLongitude < -180.0) {
            minimumLongitude += 360.0;
            maximumLongitude += 360.0;
        }
        while (minimumLongitude > 180.0) {
            minimumLongitude -= 360.0;
            maximumLongitude -= 360.0;
        }

        if (maximumLongitude <= 180.0) {
            return List.of(new GnisBounds(
                    minimumLongitude,
                    maximumLongitude,
                    minimumLatitude,
                    maximumLatitude));
        }
        return List.of(
                new GnisBounds(
                        minimumLongitude,
                        180.0,
                        minimumLatitude,
                        maximumLatitude),
                new GnisBounds(
                        -180.0,
                        maximumLongitude - 360.0,
                        minimumLatitude,
                maximumLatitude));
    }

    /** Converts a negative longitude to its equivalent value in {@code 0..360}. */
    private static double shiftLongitude(double longitude) {
        return longitude < 0.0 ? longitude + 360.0 : longitude;
    }

    /** Converts an internally shifted longitude back to {@code -180..180}. */
    private static double normalizeLongitude(double longitude) {
        return longitude > 180.0 ? longitude - 360.0 : longitude;
    }
}
