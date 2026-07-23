package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;

import us.dot.its.jpo.asn.j2735.r2024.Common.LaneWidth;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.timvalidator.validator.OffsetPathDecoder.DecodedPath;

/** Geometry-related best-practices checks for a TIM geographical path. */
final class GeometryValidator {

    private static final Coordinate ANCHOR = new Coordinate(0.0, 0.0);
    private static final double CENTIMETERS_PER_METER = 100.0;
    private static final double REQUIRED_ANCHOR_TO_FIRST_NODE_CM = 1_000.0;
    private static final double ANCHOR_DISTANCE_TOLERANCE_CM = 100.0;
    private static final double ANGLE_EPSILON_RADIANS = 1.0e-12;
    private static final double WIDTH_COMPARISON_EPSILON_CM = 1.0e-6;

    private GeometryValidator() {
    }

    static List<String> validate(GeographicalPath region, int dataFrameIndex, int regionIndex) {
        Optional<DecodedPath> decodedPath = OffsetPathDecoder.decode(region);
        if (decodedPath.isEmpty()) {
            // Geometry choices that cannot be decoded are handled by schema validation.
            return List.of();
        }

        List<Coordinate> nodes = decodedPath.orElseThrow().nodes();
        if (nodes.isEmpty()) {
            return List.of();
        }

        List<String> issues = new ArrayList<>();
        boolean closedPath = region.getClosedPath() != null && region.getClosedPath().getValue();
        issues.addAll(PathTopologyValidator.validate(nodes, closedPath, dataFrameIndex, regionIndex));
        validateAnchor(issues, nodes.getFirst(), dataFrameIndex, regionIndex);
        if (!closedPath) {
            validateLaneWidth(issues, region, nodes, dataFrameIndex, regionIndex);
        }
        return issues;
    }

    private static void validateAnchor(
            List<String> issues,
            Coordinate firstNode,
            int dataFrameIndex,
            int regionIndex) {
        double distanceCm = ANCHOR.distance(firstNode);
        if (Math.abs(distanceCm - REQUIRED_ANCHOR_TO_FIRST_NODE_CM) <= ANCHOR_DISTANCE_TOLERANCE_CM) {
            return;
        }

        issues.add(String.format(
                Locale.ROOT,
                "Data frame %d region %d anchor must be 10.00 m before the first path node; actual distance is %.2f m",
                dataFrameIndex,
                regionIndex,
                distanceCm / CENTIMETERS_PER_METER));
    }

    private static void validateLaneWidth(
            List<String> issues,
            GeographicalPath region,
            List<Coordinate> nodes,
            int dataFrameIndex,
            int regionIndex) {
        LaneWidth laneWidth = region.getLaneWidth();
        if (laneWidth == null || laneWidth.getValue() <= 0 || nodes.size() < 3) {
            return;
        }

        WidthLimit limit = maximumCenteredLaneWidth(nodes);
        double laneWidthCm = laneWidth.getValue();
        if (laneWidthCm - limit.widthCm() <= WIDTH_COMPARISON_EPSILON_CM) {
            return;
        }

        issues.add(String.format(
                Locale.ROOT,
                "Data frame %d region %d laneWidth %.2f cm exceeds maximum allowable centered path width %.2f cm "
                        + "at point index %d",
                dataFrameIndex,
                regionIndex,
                laneWidthCm,
                limit.widthCm(),
                limit.pointIndex()));
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
        int pointCount = nodes.size();

        WidthLimit limit = new WidthLimit(Double.POSITIVE_INFINITY, -1);
        for (int pointIndex = 1; pointIndex <= pointCount - 2; pointIndex++) {
            int previousIndex = pointIndex - 1;
            int nextIndex = pointIndex + 1;
            double maximumWidthCm = maximumCenteredLaneWidthAtBend(
                    nodes.get(previousIndex),
                    nodes.get(pointIndex),
                    nodes.get(nextIndex));

            if (maximumWidthCm < limit.widthCm()) {
                limit = new WidthLimit(maximumWidthCm, pointIndex);
            }
        }

        return limit;
    }

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

    private record WidthLimit(double widthCm, int pointIndex) {
    }
}
