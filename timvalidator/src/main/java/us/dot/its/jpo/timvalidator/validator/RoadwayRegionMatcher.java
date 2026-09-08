package us.dot.its.jpo.timvalidator.validator;

import static us.dot.its.jpo.timvalidator.validator.AngleDegreesUtils.normalizeDegrees;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.AffineTransformation;
import org.locationtech.proj4j.units.Units;

import us.dot.its.jpo.asn.j2735.r2024.Common.Position3D;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Circle;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.road.RoadGeometryProvider;
import us.dot.its.jpo.timvalidator.road.RoadSegment;
import us.dot.its.jpo.timvalidator.validator.OffsetPathDecoder.DecodeResult;

/**
 * Retrieves roadway geometry for a TIM area and returns bearings of every
 * positive-length roadway portion inside that area.
 */
final class RoadwayRegionMatcher {

    private static final double ROAD_QUERY_BUFFER_METERS = 5.0;
    private static final double CLOSED_RING_EPSILON_CENTIMETERS = 1.0e-6;
    private static final double MINIMUM_ROAD_PORTION_LENGTH_METERS = 1.0e-6;
    private static final int CIRCLE_APPROXIMATION_QUADRANT_SEGMENTS = 32;
    private static final double METERS_PER_CENTIMETER = Units.convert(
            1.0,
            Units.CENTIMETRES,
            Units.METRES);
    private static final Coordinate ORIGIN = new Coordinate(0.0, 0.0);
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private final RoadGeometryProvider roadGeometryProvider;

    RoadwayRegionMatcher(RoadGeometryProvider roadGeometryProvider) {
        this.roadGeometryProvider = Objects.requireNonNull(
                roadGeometryProvider,
                "roadGeometryProvider");
    }

    /**
     * Returns an empty optional when the TIM region cannot be decoded. A present,
     * empty list means the region was decoded but no positive-length roadway
     * portion was found inside it.
     */
    Optional<List<RoadwayBearing>> findRoadwayBearings(
            GeographicalPath region,
            Circle circle) throws ValidationException {
        return circle == null
                ? closedPathRoadwayBearings(region)
                : circleRoadwayBearings(circle);
    }

    private Optional<List<RoadwayBearing>> circleRoadwayBearings(Circle circle)
            throws ValidationException {
        Optional<Coordinate> center = OffsetPathDecoder.wgs84Coordinate(circle.getCenter());
        OptionalDouble radiusMeters = CircleGeometryUtils.radiusMeters(circle);
        if (center.isEmpty() || radiusMeters.isEmpty()) {
            return Optional.empty();
        }

        Coordinate circleCenter = center.orElseThrow();
        double radius = radiusMeters.orElseThrow();
        List<RoadSegment> roads = roadGeometryProvider.findNearbyRoads(
                circleCenter,
                radius);
        Geometry circleArea = GEOMETRY_FACTORY.createPoint(ORIGIN)
                .buffer(radius, CIRCLE_APPROXIMATION_QUADRANT_SEGMENTS);
        return Optional.of(roadwayBearingsInsideArea(
                circleCenter,
                circleArea,
                roads));
    }

    private Optional<List<RoadwayBearing>> closedPathRoadwayBearings(
            GeographicalPath region) throws ValidationException {
        if (region.getClosedPath() == null || !region.getClosedPath().getValue()) {
            return Optional.empty();
        }

        Optional<Coordinate> anchor = OffsetPathDecoder.wgs84Coordinate(region.getAnchor());
        DecodeResult decodeResult = OffsetPathDecoder.decode(region);
        if (anchor.isEmpty() || !decodeResult.decoded()) {
            return Optional.empty();
        }

        Optional<Polygon> decodedPolygon =
                localPolygonMeters(decodeResult.path().nodes());
        if (decodedPolygon.isEmpty()) {
            return Optional.empty();
        }

        Polygon polygon = decodedPolygon.orElseThrow();
        Geometry bufferedSearchArea = polygon.buffer(ROAD_QUERY_BUFFER_METERS);
        if (!(bufferedSearchArea instanceof Polygon searchArea)) {
            return Optional.empty();
        }

        Optional<Polygon> transformedSearchArea =
                wgs84Polygon(region.getAnchor(), searchArea);
        if (transformedSearchArea.isEmpty()) {
            return Optional.empty();
        }

        List<RoadSegment> roads =
                roadGeometryProvider.findRoadsIn(transformedSearchArea.orElseThrow());
        return Optional.of(roadwayBearingsInsideArea(
                anchor.orElseThrow(),
                polygon,
                roads));
    }

    private Optional<Polygon> localPolygonMeters(List<Coordinate> nodesCentimeters) {
        if (nodesCentimeters.size() < 4) {
            return Optional.empty();
        }

        Coordinate[] coordinates = nodesCentimeters.stream()
                .map(Coordinate::copy)
                .toArray(Coordinate[]::new);
        if (coordinates[0].distance(coordinates[coordinates.length - 1])
                > CLOSED_RING_EPSILON_CENTIMETERS) {
            return Optional.empty();
        }
        coordinates[coordinates.length - 1] = coordinates[0].copy();

        try {
            Polygon polygonCentimeters = GEOMETRY_FACTORY.createPolygon(coordinates);
            if (!polygonCentimeters.isValid()) {
                return Optional.empty();
            }
            Geometry polygonMeters = AffineTransformation
                    .scaleInstance(
                            METERS_PER_CENTIMETER,
                            METERS_PER_CENTIMETER)
                    .transform(polygonCentimeters);
            return polygonMeters instanceof Polygon polygon
                    ? Optional.of(polygon)
                    : Optional.empty();
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Optional<Polygon> wgs84Polygon(
            Position3D anchor,
            Polygon localPolygonMeters) {
        Optional<List<Coordinate>> transformed = OffsetPathDecoder.wgs84Coordinates(
                anchor,
                localPolygonMeters.getExteriorRing().getCoordinates());
        if (transformed.isEmpty()) {
            return Optional.empty();
        }

        Coordinate[] coordinates =
                transformed.orElseThrow().toArray(Coordinate[]::new);
        coordinates[coordinates.length - 1] = coordinates[0].copy();
        try {
            Polygon polygon = GEOMETRY_FACTORY.createPolygon(coordinates);
            return polygon.isValid()
                    ? Optional.of(polygon)
                    : Optional.empty();
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private List<RoadwayBearing> roadwayBearingsInsideArea(
            Coordinate projectionOrigin,
            Geometry regionArea,
            List<RoadSegment> roads) {
        if (roads == null) {
            return List.of();
        }

        List<RoadwayBearing> bearings = new ArrayList<>();
        for (RoadSegment road : roads) {
            Optional<LineString> projectedRoad =
                    projectedRoad(projectionOrigin, road);
            if (projectedRoad.isEmpty()) {
                continue;
            }

            Geometry roadwayInsideRegion =
                    projectedRoad.orElseThrow().intersection(regionArea);
            addRoadwayBearings(road, roadwayInsideRegion, bearings);
        }
        return List.copyOf(bearings);
    }

    private Optional<LineString> projectedRoad(
            Coordinate projectionOrigin,
            RoadSegment road) {
        Optional<List<Coordinate>> projectedCoordinates =
                OffsetPathDecoder.displacementsMeters(
                        projectionOrigin,
                        road.geometry().getCoordinates());
        return projectedCoordinates.map(coordinates -> GEOMETRY_FACTORY.createLineString(
                coordinates.toArray(Coordinate[]::new)));
    }

    private void addRoadwayBearings(
            RoadSegment road,
            Geometry geometry,
            List<RoadwayBearing> bearings) {
        if (geometry instanceof LineString lineString) {
            Coordinate[] coordinates = lineString.getCoordinates();
            for (int index = 1; index < coordinates.length; index++) {
                Coordinate start = coordinates[index - 1];
                Coordinate end = coordinates[index];
                if (start.distance(end) > MINIMUM_ROAD_PORTION_LENGTH_METERS) {
                    addRoadwayBearing(road, start, end, bearings);
                }
            }
            return;
        }

        for (int index = 0; index < geometry.getNumGeometries(); index++) {
            Geometry component = geometry.getGeometryN(index);
            if (component != geometry) {
                addRoadwayBearings(road, component, bearings);
            }
        }
    }

    private void addRoadwayBearing(
            RoadSegment road,
            Coordinate start,
            Coordinate end,
            List<RoadwayBearing> bearings) {
        bearings.add(new RoadwayBearing(
                road,
                normalizeDegrees(
                        90.0 - Math.toDegrees(Angle.angle(start, end)))));
    }

    record RoadwayBearing(RoadSegment road, double bearingDegrees) {
    }
}
