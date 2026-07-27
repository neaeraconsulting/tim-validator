package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.valid.IsSimpleOp;

/** Validates the decoded centerline of an open path or the boundary of a closed path. */
final class CenterlineGeometryValidator {

    private static final double COORDINATE_COMPARISON_EPSILON_CM = 1.0e-6;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    /** Prevents instantiation of this stateless utility class. */
    private CenterlineGeometryValidator() {
    }

    /**
     * Validates repeated points and self-intersections for an open centerline,
     * or closure, repeated points, and self-intersections for a closed polygon boundary.
     */
    public static List<String> validate(
            List<Coordinate> nodes,
            boolean closedPath,
            DataFrameIndexes indexes) {
        List<String> issues = new ArrayList<>();
        RepeatedPoint repeatedPoint = firstRepeatedPoint(nodes, closedPath);

        if (!closedPath) {
            if (repeatedPoint != null) {
                issues.add(repeatedPointIssue(
                        "open path",
                        repeatedPoint,
                        indexes));
            } else if (nodes.size() >= 2) {
                validateOpenPathSelfIntersection(
                        issues,
                        nodes,
                        indexes);
            }
            return issues;
        }

        boolean closedRing = samePoint(nodes.getFirst(), nodes.getLast());
        if (!closedRing) {
            issues.add(String.format(
                    Locale.ROOT,
                    "Data frame %d region %d closed polygon's first and last points must coincide; "
                            + "point indexes 0 and %d are %.2f cm apart",
                    indexes.dataFrameIndex(),
                    indexes.regionIndex(),
                    nodes.size() - 1,
                    nodes.getFirst().distance(nodes.getLast())));
        }

        if (repeatedPoint != null) {
            issues.add(repeatedPointIssue(
                    "closed polygon",
                    repeatedPoint,
                    indexes));
        }

        if (closedRing && repeatedPoint == null && nodes.size() >= 4) {
            validateClosedPathSelfIntersection(issues, nodes, indexes);
        }

        return issues;
    }

    /**
     * Finds the first pair of coincident nodes, excluding the required first/last
     * repetition when the nodes represent a closed polygon.
     */
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

    /** Determines whether two decoded coordinates coincide within the validator's numerical tolerance. */
    private static boolean samePoint(Coordinate first, Coordinate second) {
        return first.distance(second) <= COORDINATE_COMPARISON_EPSILON_CM;
    }

    /** Builds the validation message identifying the first disallowed pair of repeated points. */
    private static String repeatedPointIssue(
            String geometryName,
            RepeatedPoint repeatedPoint,
            DataFrameIndexes indexes) {
        return String.format(
                Locale.ROOT,
                "Data frame %d region %d %s must not contain repeated points; point indexes %d and %d coincide",
                indexes.dataFrameIndex(),
                indexes.regionIndex(),
                geometryName,
                repeatedPoint.firstIndex(),
                repeatedPoint.secondIndex());
    }

    /**
     * Converts an open path's decoded nodes into a line string and delegates
     * its self-intersection check to the shared topology routine.
     */
    private static void validateOpenPathSelfIntersection(
            List<String> issues,
            List<Coordinate> nodes,
            DataFrameIndexes indexes) {
        LineString path = GEOMETRY_FACTORY.createLineString(nodes.stream()
                .map(Coordinate::copy)
                .toArray(Coordinate[]::new));
        validateSelfIntersection(issues, path, "open path", indexes);
    }

    /**
     * Converts a closed path's decoded nodes into an exactly closed line string
     * and delegates its self-intersection check to the shared topology routine.
     */
    private static void validateClosedPathSelfIntersection(
            List<String> issues,
            List<Coordinate> nodes,
            DataFrameIndexes indexes) {
        Coordinate[] boundaryCoordinates = nodes.stream()
                .map(Coordinate::copy)
                .toArray(Coordinate[]::new);
        // Treat endpoints within the numerical equality tolerance as the same exact ring coordinate for JTS.
        boundaryCoordinates[boundaryCoordinates.length - 1] = boundaryCoordinates[0].copy();

        LineString boundary = GEOMETRY_FACTORY.createLineString(boundaryCoordinates);
        validateSelfIntersection(issues, boundary, "closed polygon", indexes);
    }

    /**
     * Uses JTS line simplicity to detect a self-intersection and reports its
     * location when JTS supplies one.
     */
    private static void validateSelfIntersection(
            List<String> issues,
            LineString geometry,
            String geometryName,
            DataFrameIndexes indexes) {
        IsSimpleOp simplicity = new IsSimpleOp(geometry);
        if (simplicity.isSimple()) {
            return;
        }

        Coordinate nonSimpleLocation = simplicity.getNonSimpleLocation();
        if (nonSimpleLocation == null) {
            issues.add(String.format(
                    Locale.ROOT,
                    "Data frame %d region %d %s must not intersect itself",
                    indexes.dataFrameIndex(),
                    indexes.regionIndex(),
                    geometryName));
            return;
        }

        issues.add(String.format(
                Locale.ROOT,
                "Data frame %d region %d %s must not intersect itself; "
                        + "intersection is near (%.2f cm, %.2f cm)",
                indexes.dataFrameIndex(),
                indexes.regionIndex(),
                geometryName,
                nonSimpleLocation.getX(),
                nonSimpleLocation.getY()));
    }

    private record RepeatedPoint(int firstIndex, int secondIndex) {
    }
}
