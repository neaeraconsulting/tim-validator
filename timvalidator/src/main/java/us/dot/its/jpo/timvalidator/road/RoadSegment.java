package us.dot.its.jpo.timvalidator.road;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateArrays;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * One candidate roadway centerline in WGS-84 coordinates, where x is longitude
 * and y is latitude.
 */
public record RoadSegment(long sourceId, String name, LineString geometry) {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    public RoadSegment {
        Coordinate[] coordinates = CoordinateArrays.removeRepeatedPoints(
                Objects.requireNonNull(geometry, "geometry").getCoordinates());
        if (coordinates.length < 2) {
            throw new IllegalArgumentException("A road segment requires at least two coordinates");
        }
        Arrays.stream(coordinates).forEach(RoadSegment::validateCoordinate);
        geometry = geometry.getFactory().createLineString(coordinates);
    }

    public RoadSegment(long sourceId, String name, List<Coordinate> geometry) {
        this(
                sourceId,
                name,
                GEOMETRY_FACTORY.createLineString(
                        Objects.requireNonNull(geometry, "geometry").toArray(Coordinate[]::new)));
    }

    @Override
    public LineString geometry() {
        return (LineString) geometry.copy();
    }

    private static void validateCoordinate(Coordinate coordinate) {
        Objects.requireNonNull(coordinate, "geometry coordinate");
        if (!coordinate.isValid()
                || coordinate.getY() < -90.0
                || coordinate.getY() > 90.0
                || coordinate.getX() < -180.0
                || coordinate.getX() > 180.0) {
            throw new IllegalArgumentException("Invalid WGS-84 coordinate");
        }
    }
}
