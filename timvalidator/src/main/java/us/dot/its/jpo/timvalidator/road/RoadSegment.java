package us.dot.its.jpo.timvalidator.road;

import static us.dot.its.jpo.timvalidator.road.GeoUtils.validateCoordinate;

import java.util.List;
import java.util.Objects;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateArrays;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import us.dot.its.jpo.timvalidator.exception.InvalidGeometryException;

/**
 * One candidate roadway centerline in WGS-84 coordinates, where x is longitude
 * and y is latitude.
 */
public record RoadSegment(long sourceId, String name, LineString geometry) {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    /** Builds a RoadSegment with geometry validation checks */
    public static RoadSegment validRoadSegment(long sourceId, String name, List<Coordinate> coordinates)
            throws InvalidGeometryException {
        return validRoadSegment(
            sourceId,
            name,
            GEOMETRY_FACTORY.createLineString(
                Objects.requireNonNull(coordinates, "geometry").toArray(Coordinate[]::new)));
    }

    /** Builds a RoadSegment with geometry validation checks */
    public static RoadSegment validRoadSegment(long sourceId, String name, LineString geometry)
            throws InvalidGeometryException {
        Coordinate[] coordinates = CoordinateArrays.removeRepeatedPoints(
            Objects.requireNonNull(geometry, "geometry").getCoordinates());
        if (coordinates.length < 2) {
            throw new IllegalArgumentException("A road segment requires at least two coordinates");
        }
        for (var coord : geometry.getCoordinates()) {
            validateCoordinate(coord);
        }
        LineString validGeometry = geometry.getFactory().createLineString(coordinates);
        return new RoadSegment(sourceId, name, validGeometry);
    }

    @Override
    public LineString geometry() {
        return (LineString) geometry.copy();
    }

}
