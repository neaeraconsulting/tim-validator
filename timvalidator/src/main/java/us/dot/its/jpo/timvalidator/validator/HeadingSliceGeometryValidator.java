package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.linearref.LinearLocation;
import org.locationtech.jts.linearref.LocationIndexedLine;

import us.dot.its.jpo.asn.j2735.r2024.Common.HeadingSlice;
import us.dot.its.jpo.asn.j2735.r2024.Common.Position3D;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeometricProjection;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.road.RoadGeometryProvider;
import us.dot.its.jpo.timvalidator.road.RoadSegment;

/** Checks that each directional heading range is tangent to the physical roadway at the TIM start. */
final class HeadingSliceGeometryValidator {

    private static final String CHECK_NAME = "Best Practices";
    private static final double COORDINATE_UNITS_PER_DEGREE = 10_000_000.0;
    private static final double SLICE_WIDTH_DEGREES = 22.5;
    // Change this value to tune the allowed +/- difference from each range midpoint.
    private static final double ROADWAY_TANGENCY_TOLERANCE_DEGREES = 22.5;
    private static final double ROAD_SEARCH_RADIUS_METERS = 30.0;
    private static final double CANDIDATE_DISTANCE_TOLERANCE_METERS = 8.0;
    // Covers insignificant local-projection convergence at a heading-slice boundary.
    private static final double ANGLE_EPSILON_DEGREES = 1.0e-3;
    private static final Coordinate ORIGIN = new Coordinate(0.0, 0.0);
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private final RoadGeometryProvider roadGeometryProvider;

    HeadingSliceGeometryValidator(RoadGeometryProvider roadGeometryProvider) {
        this.roadGeometryProvider = Objects.requireNonNull(
                roadGeometryProvider,
                "roadGeometryProvider");
    }

    List<ValidationIssue> validate(
            GeographicalPath region,
            int dataFrameIndex,
            int regionIndex) {
        HeadingSelection selection = headingSelection(region);
        if (selection == null) {
            return List.of();
        }

        List<HeadingRange> ranges = directionalRanges(selection.heading());
        if (ranges.isEmpty()) {
            // noHeading (0000) and allHeadings (ffff) do not have a center direction.
            return List.of();
        }

        String issuePath = regionPath(dataFrameIndex, regionIndex) + selection.pathSuffix();
        Coordinate start = startPoint(region);
        if (start == null) {
            // Schema validation reports missing or invalid anchor coordinates.
            return List.of();
        }

        List<RoadSegment> roads;
        try {
            roads = roadGeometryProvider.findNearbyRoads(start, ROAD_SEARCH_RADIUS_METERS);
        } catch (RuntimeException ex) {
            return List.of(warning(
                    String.format(
                            Locale.ROOT,
                            "Data frame %d region %d roadway heading could not be evaluated: %s",
                            dataFrameIndex,
                            regionIndex,
                            safeMessage(ex)),
                    issuePath));
        }

        if (roads == null) {
            roads = List.of();
        }
        List<RoadMatch> matches = roads.stream()
                .map(road -> closestMatch(start, road))
                .filter(match -> match != null
                        && match.distanceMeters() <= ROAD_SEARCH_RADIUS_METERS)
                .sorted(Comparator.comparingDouble(RoadMatch::distanceMeters))
                .toList();
        if (matches.isEmpty()) {
            return List.of(warning(
                    String.format(
                            Locale.ROOT,
                            "Data frame %d region %d has no mapped roadway within %.1f m of the TIM start",
                            dataFrameIndex,
                            regionIndex,
                            ROAD_SEARCH_RADIUS_METERS),
                    issuePath));
        }

        List<RoadMatch> candidates = candidateMatches(matches);

        List<HeadingRange> mismatches = ranges.stream()
                .filter(range -> candidates.stream().noneMatch(
                        candidate -> isTangent(
                                range.centerDegrees(),
                                candidate.bearingDegrees())))
                .toList();
        if (mismatches.isEmpty()) {
            return List.of();
        }

        return List.of(warning(
                String.format(
                        Locale.ROOT,
                        "Data frame %d region %d heading range center(s) %s are not tangent to any mapped "
                                + "roadway candidate at the TIM start; candidates: %s",
                        dataFrameIndex,
                        regionIndex,
                        formatRanges(mismatches),
                        formatCandidates(candidates)),
                issuePath));
    }

    private HeadingSelection headingSelection(GeographicalPath region) {
        if (region == null) {
            return null;
        }
        if (region.getDirection() != null) {
            return new HeadingSelection(region.getDirection(), "/direction");
        }
        if (region.getDescription() == null) {
            return null;
        }
        GeometricProjection geometry = region.getDescription().getGeometry();
        if (geometry == null || geometry.getDirection() == null) {
            return null;
        }
        return new HeadingSelection(geometry.getDirection(), "/description/geometry/direction");
    }

    private List<HeadingRange> directionalRanges(HeadingSlice heading) {
        boolean[] active = new boolean[heading.size()];
        int activeCount = 0;
        for (int index = 0; index < active.length; index++) {
            active[index] = heading.get(index);
            if (active[index]) {
                activeCount++;
            }
        }
        if (activeCount == 0 || activeCount == active.length) {
            return List.of();
        }

        int breakIndex = 0;
        while (active[breakIndex]) {
            breakIndex++;
        }

        List<HeadingRange> ranges = new ArrayList<>();
        int rangeStart = -1;
        int rangeLength = 0;
        for (int step = 1; step <= active.length; step++) {
            int index = (breakIndex + step) % active.length;
            if (active[index]) {
                if (rangeStart < 0) {
                    rangeStart = index;
                }
                rangeLength++;
            } else if (rangeStart >= 0) {
                ranges.add(new HeadingRange(
                        rangeStart,
                        rangeLength,
                        normalizeDegrees(
                                (rangeStart + rangeLength / 2.0)
                                        * SLICE_WIDTH_DEGREES)));
                rangeStart = -1;
                rangeLength = 0;
            }
        }
        return List.copyOf(ranges);
    }

    private Coordinate startPoint(GeographicalPath region) {
        Position3D anchor = region.getAnchor();
        if (anchor == null || anchor.getLat() == null || anchor.getLong_() == null) {
            return null;
        }

        Coordinate start = new Coordinate(
                anchor.getLong_().getValue() / COORDINATE_UNITS_PER_DEGREE,
                anchor.getLat().getValue() / COORDINATE_UNITS_PER_DEGREE);
        return start.isValid()
                        && start.getY() >= -90.0
                        && start.getY() <= 90.0
                        && start.getX() >= -180.0
                        && start.getX() <= 180.0
                ? start
                : null;
    }

    private RoadMatch closestMatch(Coordinate start, RoadSegment road) {
        Optional<List<Coordinate>> projectedCoordinates =
                OffsetPathDecoder.displacementsMeters(
                        start,
                        road.geometry().getCoordinates());
        if (projectedCoordinates.isEmpty()) {
            return null;
        }

        Coordinate[] localCoordinates =
                projectedCoordinates.orElseThrow().toArray(Coordinate[]::new);
        LineString roadLine = GEOMETRY_FACTORY.createLineString(localCoordinates);
        LinearLocation nearestLocation = new LocationIndexedLine(roadLine).project(ORIGIN);
        Coordinate nearestPoint = nearestLocation.getCoordinate(roadLine);
        LineSegment tangentSegment = nearestLocation.getSegment(roadLine);
        double bearing = normalizeDegrees(
                90.0 - Math.toDegrees(Angle.angle(tangentSegment.p0, tangentSegment.p1)));

        return new RoadMatch(road, nearestPoint.distance(ORIGIN), bearing);
    }

    private List<RoadMatch> candidateMatches(List<RoadMatch> matches) {
        double maximumCandidateDistance = Math.min(
                ROAD_SEARCH_RADIUS_METERS,
                matches.getFirst().distanceMeters() + CANDIDATE_DISTANCE_TOLERANCE_METERS);
        return matches.stream()
                .filter(match -> match.distanceMeters() <= maximumCandidateDistance)
                .toList();
    }

    private boolean isTangent(double centerDegrees, double roadwayBearingDegrees) {
        return tangentAxisDistance(centerDegrees, roadwayBearingDegrees)
                <= ROADWAY_TANGENCY_TOLERANCE_DEGREES + ANGLE_EPSILON_DEGREES;
    }

    private double tangentAxisDistance(double firstDegrees, double secondDegrees) {
        double difference = Math.toDegrees(Angle.diff(
                Math.toRadians(firstDegrees),
                Math.toRadians(secondDegrees)));
        return Math.min(difference, Math.abs(180.0 - difference));
    }

    private double normalizeDegrees(double degrees) {
        double normalized = degrees % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    private String formatRanges(List<HeadingRange> ranges) {
        return ranges.stream()
                .map(range -> String.format(
                        Locale.ROOT,
                        "slices %s centered at %.1f degrees",
                        sliceIndexes(range),
                        range.centerDegrees()))
                .toList()
                .toString();
    }

    private List<Integer> sliceIndexes(HeadingRange range) {
        List<Integer> indexes = new ArrayList<>(range.length());
        for (int offset = 0; offset < range.length(); offset++) {
            indexes.add((range.startIndex() + offset) % 16);
        }
        return List.copyOf(indexes);
    }

    private String roadNameSuffix(RoadSegment road) {
        return road.name() == null || road.name().isBlank()
                ? ""
                : " (" + road.name() + ")";
    }

    private String formatCandidates(List<RoadMatch> candidates) {
        return candidates.stream()
                .map(candidate -> String.format(
                        Locale.ROOT,
                        "OSM way %d%s bearing %.2f degrees at %.2f m",
                        candidate.road().sourceId(),
                        roadNameSuffix(candidate.road()),
                        candidate.bearingDegrees(),
                        candidate.distanceMeters()))
                .toList()
                .toString();
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? ex.getClass().getSimpleName()
                : message;
    }

    private ValidationIssue warning(String message, String path) {
        return new ValidationIssue(ValidationSeverity.WARNING, CHECK_NAME, message, path);
    }

    private String regionPath(int dataFrameIndex, int regionIndex) {
        return "/value/TravelerInformation/dataFrames/" + dataFrameIndex + "/regions/" + regionIndex;
    }

    private record HeadingSelection(HeadingSlice heading, String pathSuffix) {
    }

    private record HeadingRange(int startIndex, int length, double centerDegrees) {
    }

    private record RoadMatch(RoadSegment road, double distanceMeters, double bearingDegrees) {
    }
}
