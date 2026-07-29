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
        validateLocalGeometry(region, indexes).stream()
                .map(message -> new ValidationIssue(
                        ValidationSeverity.ERROR,
                        CHECK_NAME,
                        message,
                        null))
                .forEach(issues::add);

        if (headingSliceGeometryValidator != null) {
            issues.addAll(headingSliceGeometryValidator.validate(
                    region,
                    indexes.dataFrameIndex(),
                    indexes.regionIndex()));
        }
        return List.copyOf(issues);
    }

    private List<String> validateLocalGeometry(
            GeographicalPath region,
            DataFrameIndexes indexes) {
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
        List<String> centerlineIssues =
                CenterlineGeometryValidator.validate(nodes, closedPath, indexes);
        issues.addAll(centerlineIssues);
        validateAnchor(issues, nodes.getFirst(), indexes);
        if (!closedPath) {
            issues.addAll(LaneWidthGeometryValidator.validateWidthAtBends(
                    region,
                    nodes,
                    indexes));
            if (centerlineIssues.isEmpty()) {
                issues.addAll(LaneWidthGeometryValidator.validateCorridor(
                        region,
                        nodes,
                        indexes));
            }
        }
        return issues;
    }

    private static void validateAnchor(
            List<String> issues,
            Coordinate firstNode,
            DataFrameIndexes indexes) {
        double distanceCm = ANCHOR.distance(firstNode);
        if (Math.abs(distanceCm - REQUIRED_ANCHOR_TO_FIRST_NODE_CM) <= ANCHOR_DISTANCE_TOLERANCE_CM) {
            return;
        }

        issues.add(String.format(
                Locale.ROOT,
                "Data frame %d region %d anchor must be 10.00 m before the first path node; actual distance is %.2f m",
                indexes.dataFrameIndex(),
                indexes.regionIndex(),
                distanceCm / CENTIMETERS_PER_METER));
    }
}
