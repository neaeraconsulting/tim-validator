package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.TopologyException;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.operation.buffer.OffsetCurve;
import org.locationtech.jts.operation.valid.IsSimpleOp;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;

import us.dot.its.jpo.asn.j2735.r2024.Common.LaneWidth;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;

/**
 * Validates the local and complete geometric effects of laneWidth on an open path.
 *
 * <p>Raw offset curves are used so that JTS does not dissolve a self-overlap
 * before it can be reported.</p>
 */
final class LaneWidthGeometryValidator {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final double MINIMUM_CORRIDOR_AREA_SQUARE_CM = 1.0e-6;
    private static final double ANGLE_EPSILON_RADIANS = 1.0e-12;
    private static final double WIDTH_COMPARISON_EPSILON_CM = 1.0e-6;

    /** Prevents instantiation of this stateless utility class. */
    private LaneWidthGeometryValidator() {
    }

    /**
     * Validates that the complete lane width does not exceed the local geometric
     * limit imposed by any three-point bend in the centerline.
     */
    static List<String> validateWidthAtBends(
            GeographicalPath region,
            List<Coordinate> nodes,
            DataFrameIndexes indexes) {
        LaneWidth laneWidth = region.getLaneWidth();
        if (laneWidth == null || laneWidth.getValue() <= 0 || nodes.size() < 3) {
            return List.of();
        }

        WidthLimit limit = maximumCenteredLaneWidth(nodes);
        double laneWidthCm = laneWidth.getValue();
        if (laneWidthCm - limit.widthCm() <= WIDTH_COMPARISON_EPSILON_CM) {
            return List.of();
        }

        return List.of(String.format(
                Locale.ROOT,
                "Data frame %d region %d laneWidth %.2f cm exceeds maximum allowable centered path width %.2f cm "
                        + "at point index %d",
                indexes.dataFrameIndex(),
                indexes.regionIndex(),
                laneWidthCm,
                limit.widthCm(),
                limit.pointIndex()));
    }

    /**
     * Expands the complete centerline into a lane corridor, then validates that
     * the corridor does not overlap itself.
     */
    static List<String> validateCorridor(
            GeographicalPath region,
            List<Coordinate> nodes,
            DataFrameIndexes indexes) {
        LaneWidth laneWidth = region.getLaneWidth();
        if (laneWidth == null || laneWidth.getValue() <= 0 || nodes.size() < 2) {
            return List.of();
        }

        double halfWidthCm = laneWidth.getValue() / 2.0;
        LineString centerline = GEOMETRY_FACTORY.createLineString(nodes.stream()
                .map(Coordinate::copy)
                .toArray(Coordinate[]::new));

        BufferParameters parameters = new BufferParameters();
        parameters.setJoinStyle(BufferParameters.JOIN_MITRE);
        parameters.setEndCapStyle(BufferParameters.CAP_FLAT);
        parameters.setSimplifyFactor(0.0);

        try {
            Coordinate[] leftCoordinates = OffsetCurve.rawOffset(centerline, halfWidthCm, parameters);
            Coordinate[] rightCoordinates = OffsetCurve.rawOffset(centerline, -halfWidthCm, parameters);
            if (leftCoordinates.length < 2 || rightCoordinates.length < 2) {
                return List.of(unusableCorridorIssue(indexes));
            }

            LineString leftBoundary = GEOMETRY_FACTORY.createLineString(leftCoordinates);
            LineString rightBoundary = GEOMETRY_FACTORY.createLineString(rightCoordinates);

            List<String> issues = new ArrayList<>();
            validateBoundary(issues, "left", leftBoundary, indexes);
            validateBoundary(issues, "right", rightBoundary, indexes);
            if (!issues.isEmpty()) {
                return issues;
            }

            Geometry boundaryIntersection = leftBoundary.intersection(rightBoundary);
            if (!boundaryIntersection.isEmpty()) {
                Coordinate intersection = boundaryIntersection.getCoordinate();
                issues.add(String.format(
                        Locale.ROOT,
                        "Data frame %d region %d lane corridor's left and right boundaries must not intersect%s",
                        indexes.dataFrameIndex(),
                        indexes.regionIndex(),
                        coordinateSuffix(intersection)));
                return issues;
            }

            Polygon corridor = createCorridor(leftCoordinates, rightCoordinates);
            IsValidOp validity = new IsValidOp(corridor);
            if (!validity.isValid()) {
                TopologyValidationError error = validity.getValidationError();
                Coordinate errorCoordinate = error == null ? null : error.getCoordinate();
                String detail = error == null ? "invalid corridor geometry" : error.getMessage();
                issues.add(String.format(
                        Locale.ROOT,
                        "Data frame %d region %d lane corridor must form a valid polygon: %s%s",
                        indexes.dataFrameIndex(),
                        indexes.regionIndex(),
                        detail,
                        coordinateSuffix(errorCoordinate)));
            } else if (corridor.getArea() <= MINIMUM_CORRIDOR_AREA_SQUARE_CM) {
                issues.add(unusableCorridorIssue(indexes));
            }

            return issues;
        } catch (IllegalArgumentException | TopologyException ex) {
            return List.of(String.format(
                    Locale.ROOT,
                    "Data frame %d region %d lane corridor could not be constructed: %s",
                    indexes.dataFrameIndex(),
                    indexes.regionIndex(),
                    ex.getMessage()));
        }
    }

    /**
     * Finds the maximum centered lane width allowed by every three-point bend.
     *
     * <p>For points A-B-C and interior angle {@code theta} at B, the maximum
     * half-width is {@code tan(theta / 2) * min(length(AB), length(BC))}.
     * J2735 laneWidth is the complete edge-to-edge width, so the calculated
     * half-width is doubled before it is compared with laneWidth.</p>
     */
    private static WidthLimit maximumCenteredLaneWidth(List<Coordinate> nodes) {
        WidthLimit limit = new WidthLimit(Double.POSITIVE_INFINITY, -1);
        for (int pointIndex = 1; pointIndex <= nodes.size() - 2; pointIndex++) {
            double maximumWidthCm = maximumCenteredLaneWidthAtBend(
                    nodes.get(pointIndex - 1),
                    nodes.get(pointIndex),
                    nodes.get(pointIndex + 1));

            if (maximumWidthCm < limit.widthCm()) {
                limit = new WidthLimit(maximumWidthCm, pointIndex);
            }
        }

        return limit;
    }

    /**
     * Calculates the maximum complete lane width supported by one bend using
     * its interior angle and the shorter adjacent centerline segment.
     */
    private static double maximumCenteredLaneWidthAtBend(
            Coordinate previous,
            Coordinate vertex,
            Coordinate next) {
        double incomingLength = new LineSegment(previous, vertex).getLength();
        double outgoingLength = new LineSegment(vertex, next).getLength();
        if (incomingLength == 0.0 || outgoingLength == 0.0) {
            return 0.0;
        }

        // JTS returns the interior angle between vertex->previous and vertex->next.
        double interiorAngle = Angle.angleBetween(previous, vertex, next);
        if (!Double.isFinite(interiorAngle) || interiorAngle <= ANGLE_EPSILON_RADIANS) {
            return 0.0;
        }
        if (Math.PI - interiorAngle <= ANGLE_EPSILON_RADIANS) {
            return Double.POSITIVE_INFINITY;
        }

        double maximumHalfWidthCm = Math.tan(interiorAngle / 2.0)
                * Math.min(incomingLength, outgoingLength);
        return 2.0 * maximumHalfWidthCm;
    }

    /** Validates that one generated lane boundary does not intersect itself. */
    private static void validateBoundary(
            List<String> issues,
            String boundaryName,
            LineString boundary,
            DataFrameIndexes indexes) {
        IsSimpleOp simplicity = new IsSimpleOp(boundary);
        if (simplicity.isSimple()) {
            return;
        }

        issues.add(String.format(
                Locale.ROOT,
                "Data frame %d region %d lane corridor's %s boundary must not intersect itself%s",
                indexes.dataFrameIndex(),
                indexes.regionIndex(),
                boundaryName,
                coordinateSuffix(simplicity.getNonSimpleLocation())));
    }

    /**
     * Joins the left boundary to the reversed right boundary and closes the
     * resulting coordinate sequence to form the complete lane corridor polygon.
     */
    private static Polygon createCorridor(Coordinate[] leftCoordinates, Coordinate[] rightCoordinates) {
        List<Coordinate> shell = new ArrayList<>(leftCoordinates.length + rightCoordinates.length + 1);
        for (Coordinate coordinate : leftCoordinates) {
            shell.add(coordinate.copy());
        }
        for (int index = rightCoordinates.length - 1; index >= 0; index--) {
            shell.add(rightCoordinates[index].copy());
        }
        shell.add(shell.getFirst().copy());
        return GEOMETRY_FACTORY.createPolygon(shell.toArray(Coordinate[]::new));
    }

    /** Builds the validation message used when laneWidth cannot produce a nonempty corridor. */
    private static String unusableCorridorIssue(DataFrameIndexes indexes) {
        return String.format(
                Locale.ROOT,
                "Data frame %d region %d laneWidth does not produce a usable lane corridor",
                indexes.dataFrameIndex(),
                indexes.regionIndex());
    }

    /** Formats an optional problem coordinate for inclusion in a corridor validation message. */
    private static String coordinateSuffix(Coordinate coordinate) {
        if (coordinate == null) {
            return "";
        }
        return String.format(
                Locale.ROOT,
                "; intersection is near (%.2f cm, %.2f cm)",
                coordinate.getX(),
                coordinate.getY());
    }

    private record WidthLimit(double widthCm, int pointIndex) {
    }
}
