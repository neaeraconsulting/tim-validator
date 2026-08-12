package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.validator.OffsetPathDecoder.DecodeResult;
import us.dot.its.jpo.timvalidator.validator.OffsetPathDecoder.DecodedPath;

/** Geometry-related best-practices checks for a TIM geographical path. */
final class GeometryValidator {

    private static final String CHECK_NAME = "Best Practices";
    private static final Coordinate ANCHOR = new Coordinate(0.0, 0.0);
    private static final double CENTIMETERS_PER_METER = 100.0;
    private static final double REQUIRED_ANCHOR_TO_FIRST_NODE_CM = 1_000.0;
    private static final double ANCHOR_DISTANCE_TOLERANCE_CM = 100.0;

    private GeometryValidator() {
    }

    public static List<ValidationIssue> validate(GeographicalPath region, DataFrameIndexes indexes) {
        // Geometric projections such as circles do not contain offset paths.
        if (region != null
                && region.getDescription() != null
                && region.getDescription().getGeometry() != null) {
            return List.of();
        }

        DecodeResult decodeResult = OffsetPathDecoder.decode(region);
        String regionPath = regionPath(indexes);
        if (!decodeResult.decoded()) {
            return List.of(warning(
                    notEvaluatedWarning(indexes, decodeResult.failureReason()),
                    regionPath + decodeResult.failurePathSuffix()));
        }

        DecodedPath decodedPath = decodeResult.path();
        List<Coordinate> nodes = decodedPath.nodes();
        String nodesPath = regionPath + decodedPath.nodesPathSuffix();
        if (nodes.isEmpty()) {
            return List.of(warning(notEvaluatedWarning(indexes, "an empty path"), nodesPath));
        }

        List<ValidationIssue> issues = new ArrayList<>();
        boolean closedPath = region.getClosedPath() != null && region.getClosedPath().getValue();
        List<ValidationIssue> centerlineIssues =
                CenterlineGeometryValidator.validate(nodes, closedPath, indexes, nodesPath);
        issues.addAll(centerlineIssues);
        String anchorPath = regionPath + "/anchor";
        validateAnchorDistance(issues, nodes.getFirst(), indexes, anchorPath);
        validateAnchorApproach(issues, nodes, indexes, anchorPath, nodesPath);
        if (!closedPath) {
            String laneWidthPath = regionPath + "/laneWidth";
            issues.addAll(LaneWidthGeometryValidator.validateWidthAtBends(
                    region,
                    nodes,
                    indexes,
                    laneWidthPath));
            if (centerlineIssues.isEmpty()) {
                issues.addAll(LaneWidthGeometryValidator.validateCorridor(
                        region,
                        nodes,
                        indexes,
                        laneWidthPath,
                        nodesPath));
            }
        }
        return List.copyOf(issues);
    }

    private static void validateAnchorDistance(
            List<ValidationIssue> issues,
            Coordinate firstNode,
            DataFrameIndexes indexes,
            String anchorPath) {
        double distanceCm = ANCHOR.distance(firstNode);
        if (Math.abs(distanceCm - REQUIRED_ANCHOR_TO_FIRST_NODE_CM) <= ANCHOR_DISTANCE_TOLERANCE_CM) {
            return;
        }

        issues.add(error(String.format(
                Locale.ROOT,
                "Data frame %d region %d anchor must be 10.00 m before the first path node; actual distance is %.2f m",
                indexes.dataFrameIndex(),
                indexes.regionIndex(),
                distanceCm / CENTIMETERS_PER_METER), anchorPath));
    }

    /**
     * Checks that the anchor is before the first node relative to the first segment.
     *
     * <p>The decoded anchor is the origin. A negative projection factor places the
     * anchor before the first node along the line defined by the first segment. This
     * deliberately does not require the anchor to lie on the first segment's backward
     * extension, since that would incorrectly reject curved approaches.</p>
     */
    private static void validateAnchorApproach(
            List<ValidationIssue> issues,
            List<Coordinate> nodes,
            DataFrameIndexes indexes,
            String anchorPath,
            String nodesPath) {
        if (nodes.size() < 2) {
            issues.add(warning(notEvaluatedWarning(indexes,
                    "fewer than two path nodes for the anchor approach check"), nodesPath));
            return;
        }

        Coordinate firstNode = nodes.get(0);
        Coordinate secondNode = nodes.get(1);
        LineSegment approach = new LineSegment(firstNode, secondNode);
        if (approach.getLength() == 0.0) {
            issues.add(warning(notEvaluatedWarning(indexes,
                    "a zero-length first segment for the anchor approach check"), nodesPath + "/1"));
            return;
        }

        if (approach.projectionFactor(ANCHOR) < 0.0) {
            return;
        }

        issues.add(warning(String.format(
                Locale.ROOT,
                "Data frame %d region %d anchor must be before the first path node relative to the first path segment's direction",
                indexes.dataFrameIndex(),
                indexes.regionIndex()), anchorPath));
    }

    private static String notEvaluatedWarning(DataFrameIndexes indexes, String reason) {
        return String.format(
                Locale.ROOT,
                "Data frame %d region %d geometry not evaluated due to %s",
                indexes.dataFrameIndex(),
                indexes.regionIndex(),
                reason);
    }

    private static String regionPath(DataFrameIndexes indexes) {
        return String.format(
                Locale.ROOT,
                "/value/TravelerInformation/dataFrames/%d/regions/%d",
                indexes.dataFrameIndex(),
                indexes.regionIndex());
    }

    private static ValidationIssue error(String message, String path) {
        return new ValidationIssue(ValidationSeverity.ERROR, CHECK_NAME, message, path);
    }

    private static ValidationIssue warning(String message, String path) {
        return new ValidationIssue(ValidationSeverity.WARNING, CHECK_NAME, message, path);
    }
}
