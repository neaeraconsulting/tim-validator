package us.dot.its.jpo.timvalidator.road;

import java.util.List;
import java.util.Objects;

import org.locationtech.jts.algorithm.MinimumBoundingCircle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.proj4j.geodesic.Geodesic;

/**
 * Supplies roadway centerlines within a TIM region.
 *
 * <p>Implementations may use OpenStreetMap, an authoritative roadway database,
 * or another source. Callers are responsible for choosing applicable candidates
 * and evaluating their geometry.</p>
 */
@FunctionalInterface
public interface RoadGeometryProvider {

    /**
     * Finds roads near a WGS-84 coordinate, where x is longitude and y is latitude.
     */
    List<RoadSegment> findNearbyRoads(Coordinate location, double radiusMeters) throws RoadGeometryLookupException;

    /**
     * Finds roads within a WGS-84 polygon.
     *
     * <p>The default preserves compatibility with point-radius providers by using
     * JTS's minimum bounding circle and Proj4J's WGS-84 geodesic distance. Providers
     * that support polygon queries should override this method.</p>
     */
    default List<RoadSegment> findRoadsIn(Polygon searchArea) throws RoadGeometryLookupException {
        Objects.requireNonNull(searchArea, "searchArea");
        if (searchArea.isEmpty()) {
            throw new IllegalArgumentException("searchArea must not be empty");
        }

        MinimumBoundingCircle boundingCircle = new MinimumBoundingCircle(searchArea);
        Coordinate center = boundingCircle.getCentre();
        double radiusMeters = 0.0;
        for (Coordinate coordinate : searchArea.getCoordinates()) {
            radiusMeters = Math.max(
                    radiusMeters,
                    Geodesic.WGS84.Inverse(
                            center.getY(),
                            center.getX(),
                            coordinate.getY(),
                            coordinate.getX()).s12);
        }
        if (!Double.isFinite(radiusMeters) || radiusMeters <= 0.0) {
            throw new IllegalArgumentException("searchArea must have a positive radius");
        }
        return findNearbyRoads(center, radiusMeters);
    }
}
