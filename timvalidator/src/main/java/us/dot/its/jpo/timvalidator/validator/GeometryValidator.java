package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.locationtech.jts.geom.Coordinate;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.DistanceUnits;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.road.RoadGeometryProvider;
import us.dot.its.jpo.timvalidator.validator.OffsetPathDecoder.DecodeResult;
import us.dot.its.jpo.timvalidator.validator.OffsetPathDecoder.DecodedPath;

/** Geometry-related best-practices checks for a TIM geographical path. */
final class GeometryValidator {

    private static final String CHECK_NAME = "Best Practices";
    private static final Coordinate ANCHOR = new Coordinate(0.0, 0.0);
    private static final double CENTIMETERS_PER_METER = 100.0;
    private static final double REQUIRED_ANCHOR_TO_FIRST_NODE_CM = 1_000.0;
    private static final double ANCHOR_DISTANCE_TOLERANCE_CM = 100.0;
    private static final long MAX_SUSPICIOUS_LANE_WIDTH_CM = 20L;

    private final HeadingSliceGeometryValidator headingSliceGeometryValidator;

    GeometryValidator() {
        this.headingSliceGeometryValidator = null;
    }

    GeometryValidator(RoadGeometryProvider roadGeometryProvider) {
        this.headingSliceGeometryValidator =
                new HeadingSliceGeometryValidator(roadGeometryProvider);
    }

    List<ValidationIssue> validate(
            GeographicalPath region,
            DataFrameIndexes indexes) {
        List<ValidationIssue> issues = new ArrayList<>();
        validateComputedLaneReference(region, indexes).ifPresent(issues::add);
        validateSuspiciousLaneWidth(region, indexes).ifPresent(issues::add);
        validateCircleUnits(region, indexes).ifPresent(issues::add);
        issues.addAll(validateLocalGeometry(region, indexes));

        if (headingSliceGeometryValidator != null) {
            issues.addAll(headingSliceGeometryValidator.validate(
                    region,
                    indexes.dataFrameIndex(),
                    indexes.regionIndex()));
        }
        return List.copyOf(issues);
    }

    /**
     * Warns when a computed lane is used because its reference lane should be the
     * left-most lane in the direction of traffic.
     */
    private Optional<ValidationIssue> validateComputedLaneReference(
            GeographicalPath region,
            DataFrameIndexes indexes) {
        if (!hasComputedLane(region)) {
            return Optional.empty();
        }

        String message = String.format(
                Locale.ROOT,
                "Data frame %d region %d uses a computed lane. It is recommended that referenceLaneId "
                        + "identify the left-most lane in the direction of traffic",
                indexes.dataFrameIndex(),
                indexes.regionIndex());
        String path = String.format(
                Locale.ROOT,
                "/value/TravelerInformation/dataFrames/%d/regions/%d/description/path/offset/xy/"
                        + "computed/referenceLaneId",
                indexes.dataFrameIndex(),
                indexes.regionIndex());
        return Optional.of(new ValidationIssue(
                ValidationSeverity.WARNING,
                CHECK_NAME,
                message,
                path));
    }

    /** Validates locally decodable offset paths and skips other description choices. */
    private List<ValidationIssue> validateLocalGeometry(
            GeographicalPath region,
            DataFrameIndexes indexes) {
        if (hasComputedLane(region)) {
            return List.of();
        }
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
        OffsetEncodingRecommendationValidator.validate(
                region,
                decodedPath,
                indexes,
                regionPath).ifPresent(issues::add);
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

    /** Warns about very small values that may have been entered as meters instead of centimeters. */
    private Optional<ValidationIssue> validateSuspiciousLaneWidth(
            GeographicalPath region,
            DataFrameIndexes indexes) {
        if (region == null
                || region.getLaneWidth() == null
                || region.getLaneWidth().getValue() < 1L
                || region.getLaneWidth().getValue() > MAX_SUSPICIOUS_LANE_WIDTH_CM) {
            return Optional.empty();
        }

        long laneWidthCm = region.getLaneWidth().getValue();
        return Optional.of(warning(String.format(
                Locale.ROOT,
                "Data frame %d region %d laneWidth is %d cm, which is suspiciously small; "
                        + "J2735 laneWidth is expressed in centimeters, so verify that a value in meters was not supplied",
                indexes.dataFrameIndex(),
                indexes.regionIndex(),
                laneWidthCm), regionPath(indexes) + "/laneWidth"));
    }

    /** Warns when circle radius units are imperial because metric units are recommended for TIMs. */
    private Optional<ValidationIssue> validateCircleUnits(
            GeographicalPath region,
            DataFrameIndexes indexes) {
        if (region == null
                || region.getDescription() == null
                || region.getDescription().getGeometry() == null
                || region.getDescription().getGeometry().getCircle() == null) {
            return Optional.empty();
        }

        DistanceUnits units = region.getDescription().getGeometry().getCircle().getUnits();
        if (units == null || isMetric(units)) {
            return Optional.empty();
        }

        return Optional.of(warning(String.format(
                Locale.ROOT,
                "Data frame %d region %d circle uses %s units; metric units are recommended for TIM circle geometry",
                indexes.dataFrameIndex(),
                indexes.regionIndex(),
                units), regionPath(indexes) + "/description/geometry/circle/units"));
    }

    /** Returns whether a J2735 distance unit is metric. */
    private static boolean isMetric(DistanceUnits units) {
        return units == DistanceUnits.CENTIMETER
                || units == DistanceUnits.CM2_5
                || units == DistanceUnits.DECIMETER
                || units == DistanceUnits.METER
                || units == DistanceUnits.KILOMETER;
    }

    private static boolean hasComputedLane(GeographicalPath region) {
        return region != null
                && region.getDescription() != null
                && region.getDescription().getPath() != null
                && region.getDescription().getPath().getOffset() != null
                && region.getDescription().getPath().getOffset().getXy() != null
                && region.getDescription().getPath().getOffset().getXy().getComputed() != null;
    }

    /** Checks that the first path node is approximately ten meters from the anchor. */
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
     * <p>The decoded anchor is the origin. For anchor A, first node P0, and second
     * node P1, a positive dot product between {@code P0 - A} and {@code P1 - P0}
     * places A in the backward half-plane of P0. This deliberately does not require
     * the anchor to lie on the first segment's backward extension, since that would
     * incorrectly reject curved approaches.</p>
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
        double approachX = secondNode.getX() - firstNode.getX();
        double approachY = secondNode.getY() - firstNode.getY();
        double approachLengthCm = Math.hypot(approachX, approachY);
        if (approachLengthCm == 0.0) {
            issues.add(warning(notEvaluatedWarning(indexes,
                    "a zero-length first segment for the anchor approach check"), nodesPath + "/1"));
            return;
        }

        double anchorToFirstX = firstNode.getX() - ANCHOR.getX();
        double anchorToFirstY = firstNode.getY() - ANCHOR.getY();
        double directionDotProduct = anchorToFirstX * approachX + anchorToFirstY * approachY;
        if (directionDotProduct > 0.0) {
            return;
        }

        issues.add(warning(String.format(
                Locale.ROOT,
                "Data frame %d region %d anchor must be before the first path node relative to the first path segment's direction",
                indexes.dataFrameIndex(),
                indexes.regionIndex()), anchorPath));
    }

    /** Creates the message used when a local geometry check cannot be performed. */
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
