package us.dot.its.jpo.timvalidator.validator;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.locationtech.jts.geom.Coordinate;

import us.dot.its.jpo.asn.j2735.r2024.Common.NodeXY;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.NodeLL;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.OffsetSystem;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.validator.OffsetPathDecoder.DecodedPath;

/** Recommends a J2735 node representation based on the complete path's size. */
final class OffsetEncodingRecommendationValidator {

    private static final String CHECK_NAME = "Best Practices";
    private static final double CENTIMETERS_PER_METER = 100.0;
    private static final double MAX_XY_PATH_SEPARATION_CM = 32_767.0;
    private static final double MAX_LL_COMPONENT_SEPARATION_DEGREES = 0.8388607;
    private static final double ANGULAR_COMPARISON_EPSILON_DEGREES = 1e-9;

    private OffsetEncodingRecommendationValidator() {
    }

    /** Warns when the encoded node representation differs from the size-based recommendation. */
    static Optional<ValidationIssue> validate(
            GeographicalPath region,
            DecodedPath decodedPath,
            DataFrameIndexes indexes,
            String regionPath) {
        Encoding actual = actualEncoding(region);
        if (actual == null) {
            return Optional.empty();
        }

        double maximumSeparationCm = maximumPairwiseDistance(decodedPath.nodes());
        Encoding recommended;
        String thresholdExplanation;
        if (maximumSeparationCm <= MAX_XY_PATH_SEPARATION_CM) {
            recommended = Encoding.XY;
            thresholdExplanation = String.format(
                    Locale.ROOT,
                    "its maximum node separation is %.2f m (at most 327.67 m)",
                    maximumSeparationCm / CENTIMETERS_PER_METER);
        } else {
            Optional<Double> angularSeparation = maximumAngularComponentSeparation(
                    region,
                    decodedPath.nodes());
            if (angularSeparation.isEmpty()) {
                return Optional.empty();
            }

            double maximumDegrees = angularSeparation.orElseThrow();
            if (maximumDegrees
                    <= MAX_LL_COMPONENT_SEPARATION_DEGREES + ANGULAR_COMPARISON_EPSILON_DEGREES) {
                recommended = Encoding.LL;
                thresholdExplanation = String.format(
                        Locale.ROOT,
                        "its maximum node separation is %.2f m and its maximum latitude/longitude "
                                + "component separation is %.7f degrees (at most 0.8388607 degrees)",
                        maximumSeparationCm / CENTIMETERS_PER_METER,
                        maximumDegrees);
            } else {
                recommended = Encoding.ABSOLUTE_LAT_LON;
                thresholdExplanation = String.format(
                        Locale.ROOT,
                        "its maximum latitude/longitude component separation is %.7f degrees "
                                + "(greater than 0.8388607 degrees)",
                        maximumDegrees);
            }
        }

        if (actual == recommended) {
            return Optional.empty();
        }

        String message = String.format(
                Locale.ROOT,
                "Data frame %d region %d uses %s nodes; %s nodes are recommended because %s",
                indexes.dataFrameIndex(),
                indexes.regionIndex(),
                actual.displayName,
                recommended.displayName,
                thresholdExplanation);
        return Optional.of(new ValidationIssue(
                ValidationSeverity.WARNING,
                CHECK_NAME,
                message,
                regionPath + "/description/path/offset"));
    }

    /** Finds the largest planar distance between any two decoded JTS coordinates. */
    private static double maximumPairwiseDistance(List<Coordinate> nodes) {
        double maximum = 0.0;
        for (int first = 0; first < nodes.size(); first++) {
            for (int second = first + 1; second < nodes.size(); second++) {
                maximum = Math.max(maximum, nodes.get(first).distance(nodes.get(second)));
            }
        }
        return maximum;
    }

    /** Uses the existing Proj4J decoder conversion to compare WGS-84 coordinate components. */
    private static Optional<Double> maximumAngularComponentSeparation(
            GeographicalPath region,
            List<Coordinate> localNodesCentimeters) {
        Optional<List<Coordinate>> geographicNodes = OffsetPathDecoder.wgs84Coordinates(
                region,
                localNodesCentimeters);
        if (geographicNodes.isEmpty()) {
            return Optional.empty();
        }

        List<Coordinate> nodes = geographicNodes.orElseThrow();
        double maximum = 0.0;
        for (int first = 0; first < nodes.size(); first++) {
            for (int second = first + 1; second < nodes.size(); second++) {
                Coordinate firstNode = nodes.get(first);
                Coordinate secondNode = nodes.get(second);
                double latitudeDifference = Math.abs(firstNode.getY() - secondNode.getY());
                double longitudeDifference = Math.abs(firstNode.getX() - secondNode.getX());
                double wrappedLongitudeDifference = Math.min(
                        longitudeDifference,
                        360.0 - longitudeDifference);
                maximum = Math.max(maximum, Math.max(
                        latitudeDifference,
                        wrappedLongitudeDifference));
            }
        }
        return Optional.of(maximum);
    }

    /** Identifies whether the path uses XY, LL, or any absolute latitude/longitude node. */
    private static Encoding actualEncoding(GeographicalPath region) {
        if (region == null
                || region.getDescription() == null
                || region.getDescription().getPath() == null
                || region.getDescription().getPath().getOffset() == null) {
            return null;
        }

        OffsetSystem.OffsetChoice offset = region.getDescription().getPath().getOffset();
        if (offset.getXy() != null && offset.getXy().getNodes() != null) {
            for (NodeXY node : offset.getXy().getNodes()) {
                if (node != null
                        && node.getDelta() != null
                        && node.getDelta().getNode_LatLon() != null) {
                    return Encoding.ABSOLUTE_LAT_LON;
                }
            }
            return Encoding.XY;
        }
        if (offset.getLl() != null && offset.getLl().getNodes() != null) {
            for (NodeLL node : offset.getLl().getNodes()) {
                if (node != null
                        && node.getDelta() != null
                        && node.getDelta().getNode_LatLon() != null) {
                    return Encoding.ABSOLUTE_LAT_LON;
                }
            }
            return Encoding.LL;
        }
        return null;
    }

    private enum Encoding {
        XY("XY offset"),
        LL("latitude/longitude offset"),
        ABSOLUTE_LAT_LON("absolute latitude/longitude");

        private final String displayName;

        Encoding(String displayName) {
            this.displayName = displayName;
        }
    }
}
