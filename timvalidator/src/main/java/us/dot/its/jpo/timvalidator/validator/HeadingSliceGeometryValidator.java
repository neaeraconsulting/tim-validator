package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.locationtech.jts.algorithm.Angle;

import us.dot.its.jpo.asn.j2735.r2024.Common.HeadingSlice;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Circle;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeometricProjection;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.road.RoadGeometryProvider;
import us.dot.its.jpo.timvalidator.road.RoadSegment;
import us.dot.its.jpo.timvalidator.validator.RoadwayRegionMatcher.RoadwayBearing;

/** Checks that each directional heading range is tangent to a roadway inside the TIM region. */
final class HeadingSliceGeometryValidator {

    private static final String CHECK_NAME = "Best Practices";
    private static final double SLICE_WIDTH_DEGREES = 22.5;
    // Change this value to tune the allowed +/- difference from each range midpoint.
    private static final double ROADWAY_TANGENCY_TOLERANCE_DEGREES = 22.5;
    // Covers insignificant local-projection convergence at a heading-slice boundary.
    private static final double ANGLE_EPSILON_DEGREES = 1.0e-3;

    private final RoadwayRegionMatcher roadwayRegionMatcher;

    HeadingSliceGeometryValidator(RoadGeometryProvider roadGeometryProvider) {
        this.roadwayRegionMatcher = new RoadwayRegionMatcher(roadGeometryProvider);
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
        Optional<List<RoadwayBearing>> evaluatedCandidates;
        try {
            evaluatedCandidates = roadwayRegionMatcher.findRoadwayBearings(
                    region,
                    selection.circle());
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

        if (evaluatedCandidates.isEmpty()) {
            return List.of(warning(
                    String.format(
                            Locale.ROOT,
                            "Data frame %d region %d roadway heading could not be evaluated because "
                                    + "the TIM region geometry is incomplete or malformed",
                            dataFrameIndex,
                            regionIndex),
                    issuePath));
        }

        List<RoadwayBearing> candidates = evaluatedCandidates.orElseThrow();
        if (candidates.isEmpty()) {
            return List.of(warning(
                    String.format(
                            Locale.ROOT,
                            "Data frame %d region %d has no mapped roadway segment inside the TIM region",
                            dataFrameIndex,
                            regionIndex),
                    issuePath));
        }

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
                                + "roadway segment inside the TIM region; candidates: %s",
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
            return new HeadingSelection(
                    region.getDirection(),
                    "/direction",
                    null);
        }
        if (region.getDescription() == null) {
            return null;
        }
        GeometricProjection geometry = region.getDescription().getGeometry();
        if (geometry == null
                || geometry.getCircle() == null
                || geometry.getDirection() == null) {
            return null;
        }
        return new HeadingSelection(
                geometry.getDirection(),
                "/description/geometry/direction",
                geometry.getCircle());
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

    private String formatCandidates(List<RoadwayBearing> candidates) {
        return candidates.stream()
                .map(candidate -> String.format(
                        Locale.ROOT,
                        "road segment %d%s bearing %.2f degrees",
                        candidate.road().sourceId(),
                        roadNameSuffix(candidate.road()),
                        candidate.bearingDegrees()))
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

    private record HeadingSelection(
            HeadingSlice heading,
            String pathSuffix,
            Circle circle) {
    }

    private record HeadingRange(int startIndex, int length, double centerDegrees) {
    }
}
