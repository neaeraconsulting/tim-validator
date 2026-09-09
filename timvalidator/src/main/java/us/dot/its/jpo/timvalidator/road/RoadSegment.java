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
 * @param sourceId the unique identifier of the source of this road segment
 * @param name the name of the road segment
 * @param geometry the geometry of the road segment represented as a LineString
 */
public record RoadSegment(long sourceId, String name, LineString geometry) {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    /** Builds a RoadSegment with geometry validation checks 
     * @param sourceId the unique identifier of the source of this road segment
     * @param name the name of the road segment
     * @param coordinates the list of coordinates representing the geometry of the road segment
     * @return a RoadSegment instance with validated geometry
     * @throws InvalidGeometryException if the geometry is invalid
    */
    public static RoadSegment validRoadSegment(long sourceId, String name, List<Coordinate> coordinates)
            throws InvalidGeometryException {
        return validRoadSegment(
            sourceId,
            name,
            GEOMETRY_FACTORY.createLineString(
                Objects.requireNonNull(coordinates, "geometry").toArray(Coordinate[]::new)));
    }

    /** Builds a RoadSegment with geometry validation checks 
     * @param sourceId the unique identifier of the source of this road segment
     * @param name the name of the road segment
     * @param geometry the geometry of the road segment represented as a LineString
     * @return a RoadSegment instance with validated geometry
     * @throws InvalidGeometryException if the geometry is invalid. A geometry is invalid if it contains less than two coordinates, or if any of the coordinate positions are out of bounds for a WSG-84 project (latitude must be between -90 and 90, longitude must be between -180 and 180)
    */
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

    /**
     * Returns a deep copy of the geometry of the road segment as provided by the geometry.copy() method
     * @return a LineString representing the geometry of the road segment
     */
    @Override
    public LineString geometry() {
        return (LineString) geometry.copy();
    }

}
