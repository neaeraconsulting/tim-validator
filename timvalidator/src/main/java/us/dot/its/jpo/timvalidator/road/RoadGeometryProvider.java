package us.dot.its.jpo.timvalidator.road;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;

/**
 * Supplies roadway centerlines near a TIM location.
 *
 * <p>Implementations may use OpenStreetMap, an authoritative roadway database,
 * or another source. Callers are responsible for choosing the best candidate
 * and evaluating its geometry.</p>
 */
@FunctionalInterface
public interface RoadGeometryProvider {

    /**
     * Finds roads near a WGS-84 coordinate, where x is longitude and y is latitude.
     */
    List<RoadSegment> findNearbyRoads(Coordinate location, double radiusMeters);
}
