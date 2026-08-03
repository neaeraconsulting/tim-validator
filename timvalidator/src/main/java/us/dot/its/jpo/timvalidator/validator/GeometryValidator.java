package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.locationtech.jts.geom.Coordinate;

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
        if (region.getDescription() == null
                || region.getDescription().getPath() == null
                || region.getDescription().getPath().getOffset() == null
                || region.getDescription().getPath().getOffset().getXy() == null
                || region.getDescription().getPath().getOffset().getXy().getComputed() == null) {
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

    /** Validates geometry that can be decoded locally from the TIM region. */
    private List<ValidationIssue> validateLocalGeometry(
            GeographicalPath region,
            DataFrameIndexes indexes) {
        DecodeResult decodeResult = OffsetPathDecoder.decode(region);
        if (!decodeResult.decoded()) {
            return List.of(warning(notEvaluatedWarning(indexes, decodeResult.failureReason())));
        }

        DecodedPath decodedPath = decodeResult.path();
        List<Coordinate> nodes = decodedPath.nodes();
        if (nodes.isEmpty()) {
            return List.of(warning(notEvaluatedWarning(indexes, "an empty path")));
        }

        List<ValidationIssue> issues = new ArrayList<>();
        boolean closedPath = region.getClosedPath() != null && region.getClosedPath().getValue();
        List<String> centerlineIssues =
                CenterlineGeometryValidator.validate(nodes, closedPath, indexes);
        addErrors(issues, centerlineIssues);
        validateAnchorDistance(issues, nodes.getFirst(), indexes);
        validateAnchorApproach(issues, nodes, indexes);
        if (!closedPath) {
            addErrors(issues, LaneWidthGeometryValidator.validateWidthAtBends(
                    region,
                    nodes,
                    indexes));
            if (centerlineIssues.isEmpty()) {
                addErrors(issues, LaneWidthGeometryValidator.validateCorridor(
                        region,
                        nodes,
                        indexes));
            }
        }
        return List.copyOf(issues);
    }

    /** Checks that the first path node is approximately ten meters from the anchor. */
    private static void validateAnchorDistance(
            List<ValidationIssue> issues,
            Coordinate firstNode,
            DataFrameIndexes indexes) {
        double distanceCm = ANCHOR.distance(firstNode);
        if (Math.abs(distanceCm - REQUIRED_ANCHOR_TO_FIRST_NODE_CM) <= ANCHOR_DISTANCE_TOLERANCE_CM) {
            return;
        }

        issues.add(error(String.format(
                Locale.ROOT,
                "Data frame %d region %d anchor must be 10.00 m before the first path node; actual distance is %.2f m",
                indexes.dataFrameIndex(),
                indexes.regionIndex(),
                distanceCm / CENTIMETERS_PER_METER)));
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
            DataFrameIndexes indexes) {
        if (nodes.size() < 2) {
            issues.add(warning(notEvaluatedWarning(indexes,
                    "fewer than two path nodes for the anchor approach check")));
            return;
        }

        Coordinate firstNode = nodes.get(0);
        Coordinate secondNode = nodes.get(1);
        double approachX = secondNode.getX() - firstNode.getX();
        double approachY = secondNode.getY() - firstNode.getY();
        double approachLengthCm = Math.hypot(approachX, approachY);
        if (approachLengthCm == 0.0) {
            issues.add(warning(notEvaluatedWarning(indexes,
                    "a zero-length first segment for the anchor approach check")));
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
                indexes.regionIndex())));
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

    /** Adds geometry error messages as structured best-practice issues. */
    private static void addErrors(List<ValidationIssue> issues, List<String> messages) {
        messages.stream()
                .map(GeometryValidator::error)
                .forEach(issues::add);
    }

    /** Creates a structured best-practice error. */
    private static ValidationIssue error(String message) {
        return new ValidationIssue(ValidationSeverity.ERROR, CHECK_NAME, message, null);
    }

    /** Creates a structured best-practice warning. */
    private static ValidationIssue warning(String message) {
        return new ValidationIssue(ValidationSeverity.WARNING, CHECK_NAME, message, null);
    }
}
