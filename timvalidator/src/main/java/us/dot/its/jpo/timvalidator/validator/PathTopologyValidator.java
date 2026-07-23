package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.valid.IsSimpleOp;

/** Validates relationships among the decoded points of an open or closed path. */
final class PathTopologyValidator {

    private static final double COORDINATE_COMPARISON_EPSILON_CM = 1.0e-6;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private PathTopologyValidator() {
    }

    static List<String> validate(
            List<Coordinate> nodes,
            boolean closedPath,
            int dataFrameIndex,
            int regionIndex) {
        List<String> issues = new ArrayList<>();
        RepeatedPoint repeatedPoint = firstRepeatedPoint(nodes, closedPath);

        if (!closedPath) {
            if (repeatedPoint != null) {
                issues.add(repeatedPointIssue(
                        "open path",
                        repeatedPoint,
                        dataFrameIndex,
                        regionIndex));
            }
            return issues;
        }

        boolean closedRing = samePoint(nodes.getFirst(), nodes.getLast());
        if (!closedRing) {
            issues.add(String.format(
                    Locale.ROOT,
                    "Data frame %d region %d closed polygon's first and last points must coincide; "
                            + "point indexes 0 and %d are %.2f cm apart",
                    dataFrameIndex,
                    regionIndex,
                    nodes.size() - 1,
                    nodes.getFirst().distance(nodes.getLast())));
        }

        if (repeatedPoint != null) {
            issues.add(repeatedPointIssue(
                    "closed polygon",
                    repeatedPoint,
                    dataFrameIndex,
                    regionIndex));
        }

        if (closedRing && repeatedPoint == null && nodes.size() >= 4) {
            validateSelfIntersection(issues, nodes, dataFrameIndex, regionIndex);
        }

        return issues;
    }

    private static RepeatedPoint firstRepeatedPoint(List<Coordinate> nodes, boolean allowClosingPoint) {
        int lastIndex = nodes.size() - 1;
        for (int firstIndex = 0; firstIndex < lastIndex; firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex <= lastIndex; secondIndex++) {
                if (allowClosingPoint && firstIndex == 0 && secondIndex == lastIndex) {
                    continue;
                }
                if (samePoint(nodes.get(firstIndex), nodes.get(secondIndex))) {
                    return new RepeatedPoint(firstIndex, secondIndex);
                }
            }
        }
        return null;
    }

    private static boolean samePoint(Coordinate first, Coordinate second) {
        return first.distance(second) <= COORDINATE_COMPARISON_EPSILON_CM;
    }

    private static String repeatedPointIssue(
            String geometryName,
            RepeatedPoint repeatedPoint,
            int dataFrameIndex,
            int regionIndex) {
        return String.format(
                Locale.ROOT,
                "Data frame %d region %d %s must not contain repeated points; point indexes %d and %d coincide",
                dataFrameIndex,
                regionIndex,
                geometryName,
                repeatedPoint.firstIndex(),
                repeatedPoint.secondIndex());
    }

    private static void validateSelfIntersection(
            List<String> issues,
            List<Coordinate> nodes,
            int dataFrameIndex,
            int regionIndex) {
        Coordinate[] boundaryCoordinates = nodes.stream()
                .map(Coordinate::copy)
                .toArray(Coordinate[]::new);
        // Treat endpoints within the numerical equality tolerance as the same exact ring coordinate for JTS.
        boundaryCoordinates[boundaryCoordinates.length - 1] = boundaryCoordinates[0].copy();

        LineString boundary = GEOMETRY_FACTORY.createLineString(boundaryCoordinates);
        IsSimpleOp simplicity = new IsSimpleOp(boundary);
        if (simplicity.isSimple()) {
            return;
        }

        Coordinate intersection = simplicity.getNonSimpleLocation();
        if (intersection == null) {
            issues.add(String.format(
                    Locale.ROOT,
                    "Data frame %d region %d closed polygon must not intersect itself",
                    dataFrameIndex,
                    regionIndex));
            return;
        }

        issues.add(String.format(
                Locale.ROOT,
                "Data frame %d region %d closed polygon must not intersect itself; "
                        + "intersection is near (%.2f cm, %.2f cm)",
                dataFrameIndex,
                regionIndex,
                intersection.getX(),
                intersection.getY()));
    }

    private record RepeatedPoint(int firstIndex, int secondIndex) {
    }
}
