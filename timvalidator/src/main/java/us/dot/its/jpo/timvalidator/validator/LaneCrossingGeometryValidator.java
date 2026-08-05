package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.TopologyException;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrameList;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.validator.OffsetPathDecoder.DecodeResult;
import us.dot.its.jpo.timvalidator.validator.OffsetPathDecoder.DecodedPath;

/** Validates geometric relationships between explicitly encoded lanes in one TIM. */
final class LaneCrossingGeometryValidator {

    private static final String CHECK_NAME = "Best Practices";
    private static final double CENTIMETERS_PER_METER = 100.0;
    private static final double SHARED_COORDINATE_PRECISION_PER_METER = 100.0;
    private static final double MINIMUM_SHARED_CENTERLINE_LENGTH_METERS = 0.01;
    private static final double MINIMUM_CORRIDOR_OVERLAP_SQUARE_METERS = 0.01;
    private static final double ENDPOINT_COMPARISON_EPSILON_METERS = 1.0e-8;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    /** Prevents instantiation of this stateless utility class. */
    private LaneCrossingGeometryValidator() {
    }

    /**
     * Compares every decodable open-path lane in the message.
     *
     * <p>A proper interior centerline crossing is an error. If the centerlines do
     * not cross, sharing a centerline segment or having lane corridor interiors
     * overlap by more than the numerical tolerance is a warning. Endpoint-only
     * contacts are allowed. At most one issue is returned for each lane pair.</p>
     */
    static List<ValidationIssue> validate(TravelerDataFrameList dataFrames) {
        if (dataFrames == null || dataFrames.isEmpty()) {
            return List.of();
        }

        Optional<Coordinate> sharedOrigin = firstLaneAnchor(dataFrames);
        if (sharedOrigin.isEmpty()) {
            return List.of();
        }

        List<LaneGeometry> lanes = decodeLanes(dataFrames, sharedOrigin.orElseThrow());
        List<ValidationIssue> issues = new ArrayList<>();
        for (int firstIndex = 0; firstIndex < lanes.size(); firstIndex++) {
            LaneGeometry first = lanes.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < lanes.size(); secondIndex++) {
                LaneGeometry second = lanes.get(secondIndex);
                compare(first, second).ifPresent(issues::add);
            }
        }
        return List.copyOf(issues);
    }

    /** Finds the first valid lane anchor to use as the message-wide projection origin. */
    private static Optional<Coordinate> firstLaneAnchor(TravelerDataFrameList dataFrames) {
        for (TravelerDataFrame dataFrame : dataFrames) {
            if (dataFrame == null || dataFrame.getRegions() == null) {
                continue;
            }
            for (GeographicalPath region : dataFrame.getRegions()) {
                if (!isExplicitOpenPath(region)) {
                    continue;
                }
                Optional<Coordinate> anchor = OffsetPathDecoder.wgs84Coordinate(region.getAnchor());
                if (anchor.isPresent()) {
                    return anchor;
                }
            }
        }
        return Optional.empty();
    }

    /** Decodes every eligible lane into the shared planar coordinate system. */
    private static List<LaneGeometry> decodeLanes(
            TravelerDataFrameList dataFrames,
            Coordinate sharedOrigin) {
        List<LaneGeometry> lanes = new ArrayList<>();
        for (int dataFrameIndex = 0; dataFrameIndex < dataFrames.size(); dataFrameIndex++) {
            TravelerDataFrame dataFrame = dataFrames.get(dataFrameIndex);
            if (dataFrame == null || dataFrame.getRegions() == null) {
                continue;
            }

            TravelerDataFrame.SequenceOfRegions regions = dataFrame.getRegions();
            for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
                GeographicalPath region = regions.get(regionIndex);
                if (!isExplicitOpenPath(region)) {
                    continue;
                }

                DataFrameIndexes indexes = new DataFrameIndexes(dataFrameIndex, regionIndex);
                decodeLane(region, indexes, sharedOrigin).ifPresent(lanes::add);
            }
        }
        return List.copyOf(lanes);
    }

    /** Builds the shared centerline and optional width corridor for one lane region. */
    private static Optional<LaneGeometry> decodeLane(
            GeographicalPath region,
            DataFrameIndexes indexes,
            Coordinate sharedOrigin) {
        DecodeResult decodeResult = OffsetPathDecoder.decode(region);
        if (!decodeResult.decoded()) {
            return Optional.empty();
        }

        DecodedPath decodedPath = decodeResult.path();
        if (decodedPath.nodes().size() < 2) {
            return Optional.empty();
        }

        Optional<List<Coordinate>> sharedCoordinates =
                OffsetPathDecoder.sharedCoordinatesMeters(
                        region,
                        decodedPath.nodes(),
                        sharedOrigin);
        if (sharedCoordinates.isEmpty()) {
            return Optional.empty();
        }

        try {
            LineString centerline = GEOMETRY_FACTORY.createLineString(
                    sharedCoordinates.orElseThrow().stream()
                            .map(LaneCrossingGeometryValidator::snapToCentimeter)
                            .toArray(Coordinate[]::new));
            // The individual-region validator reports this problem. Do not cascade it
            // into uncertain relationships with other lanes.
            if (!centerline.isSimple()) {
                return Optional.empty();
            }

            Geometry corridor = createCorridor(centerline, region);
            return Optional.of(new LaneGeometry(
                    indexes,
                    centerline,
                    corridor,
                    regionPath(indexes) + decodedPath.nodesPathSuffix()));
        } catch (IllegalArgumentException | TopologyException exception) {
            return Optional.empty();
        }
    }

    /** Buffers a centerline by half its encoded lane width using flat end caps. */
    private static Geometry createCorridor(
            LineString centerline,
            GeographicalPath region) {
        if (region.getLaneWidth() == null || region.getLaneWidth().getValue() <= 0) {
            return null;
        }

        double halfWidthMeters = region.getLaneWidth().getValue()
                / (2.0 * CENTIMETERS_PER_METER);
        BufferParameters parameters = new BufferParameters();
        parameters.setJoinStyle(BufferParameters.JOIN_MITRE);
        parameters.setEndCapStyle(BufferParameters.CAP_FLAT);
        parameters.setSimplifyFactor(0.0);
        Geometry corridor = BufferOp.bufferOp(centerline, halfWidthMeters, parameters);
        return corridor.isEmpty() ? null : corridor;
    }

    /** Removes projection noise below the centimeter resolution used by TIM XY paths. */
    private static Coordinate snapToCentimeter(Coordinate coordinate) {
        return new CoordinateXY(
                Math.rint(coordinate.getX() * SHARED_COORDINATE_PRECISION_PER_METER)
                        / SHARED_COORDINATE_PRECISION_PER_METER,
                Math.rint(coordinate.getY() * SHARED_COORDINATE_PRECISION_PER_METER)
                        / SHARED_COORDINATE_PRECISION_PER_METER);
    }

    /** Classifies one lane pair, returning at most one crossing or overlap issue. */
    private static Optional<ValidationIssue> compare(
            LaneGeometry first,
            LaneGeometry second) {
        if (!first.centerline().getEnvelopeInternal()
                .intersects(second.centerline().getEnvelopeInternal())
                && !corridorEnvelopesIntersect(first, second)) {
            return Optional.empty();
        }

        try {
            Geometry centerlineIntersection =
                    first.centerline().intersection(second.centerline());
            Coordinate crossing = interiorPointIntersection(
                    centerlineIntersection,
                    first.centerline(),
                    second.centerline());
            if (crossing != null) {
                return Optional.of(issue(
                        ValidationSeverity.ERROR,
                        first,
                        second,
                        "lane centerlines must not cross",
                        crossing));
            }

            if (centerlineIntersection.getLength()
                    > MINIMUM_SHARED_CENTERLINE_LENGTH_METERS) {
                return Optional.of(issue(
                        ValidationSeverity.WARNING,
                        first,
                        second,
                        String.format(
                                Locale.ROOT,
                                "lane centerlines overlap for %.2f m",
                                centerlineIntersection.getLength()),
                        centerlineIntersection.getCoordinate()));
            }

            // Sharing only centerline endpoints represents a possible merge or split.
            // Do not turn the unavoidable corridor contact around that point into a warning.
            if (first.centerline().touches(second.centerline())) {
                return Optional.empty();
            }

            if (first.corridor() == null || second.corridor() == null
                    || !first.corridor().getEnvelopeInternal()
                            .intersects(second.corridor().getEnvelopeInternal())) {
                return Optional.empty();
            }

            Geometry corridorOverlap = first.corridor().intersection(second.corridor());
            if (corridorOverlap.getArea() <= MINIMUM_CORRIDOR_OVERLAP_SQUARE_METERS) {
                return Optional.empty();
            }

            return Optional.of(issue(
                    ValidationSeverity.WARNING,
                    first,
                    second,
                    String.format(
                            Locale.ROOT,
                            "lane corridors overlap by %.2f square meters",
                            corridorOverlap.getArea()),
                    corridorOverlap.getCentroid().getCoordinate()));
        } catch (IllegalArgumentException | TopologyException exception) {
            // Invalid local geometry is already handled by the individual-lane checks.
            return Optional.empty();
        }
    }

    /**
     * Finds an intersection point that is interior to both complete centerlines.
     *
     * <p>Inspecting point components separately preserves crossing-error precedence
     * when the same pair also has a positive-length shared segment. An internal path
     * node counts as interior; only the complete line's first and last points are
     * treated as endpoints.</p>
     */
    private static Coordinate interiorPointIntersection(
            Geometry intersection,
            LineString first,
            LineString second) {
        if (intersection instanceof Point point) {
            Coordinate coordinate = point.getCoordinate();
            return isInterior(first, coordinate) && isInterior(second, coordinate)
                    ? coordinate
                    : null;
        }

        for (int index = 0; index < intersection.getNumGeometries(); index++) {
            Geometry component = intersection.getGeometryN(index);
            if (component == intersection) {
                continue;
            }
            Coordinate crossing = interiorPointIntersection(component, first, second);
            if (crossing != null) {
                return crossing;
            }
        }
        return null;
    }

    /** Returns whether a coordinate lies away from both endpoints of a centerline. */
    private static boolean isInterior(LineString line, Coordinate coordinate) {
        return line.getCoordinateN(0).distance(coordinate)
                        > ENDPOINT_COMPARISON_EPSILON_METERS
                && line.getCoordinateN(line.getNumPoints() - 1).distance(coordinate)
                        > ENDPOINT_COMPARISON_EPSILON_METERS;
    }

    /** Quickly determines whether both corridor envelopes intersect. */
    private static boolean corridorEnvelopesIntersect(
            LaneGeometry first,
            LaneGeometry second) {
        return first.corridor() != null
                && second.corridor() != null
                && first.corridor().getEnvelopeInternal()
                        .intersects(second.corridor().getEnvelopeInternal());
    }

    /** Creates a structured issue identifying both lane regions and the problem location. */
    private static ValidationIssue issue(
            ValidationSeverity severity,
            LaneGeometry first,
            LaneGeometry second,
            String problem,
            Coordinate location) {
        String locationSuffix = location == null
                ? ""
                : String.format(
                        Locale.ROOT,
                        "; location is near (%.2f m, %.2f m) in the shared projection",
                        location.getX(),
                        location.getY());
        String message = String.format(
                Locale.ROOT,
                "Data frame %d region %d and data frame %d region %d %s%s",
                first.indexes().dataFrameIndex(),
                first.indexes().regionIndex(),
                second.indexes().dataFrameIndex(),
                second.indexes().regionIndex(),
                problem,
                locationSuffix);
        return new ValidationIssue(severity, CHECK_NAME, message, second.nodesPath());
    }

    /** Returns whether a region contains a directly decodable, non-closed offset path. */
    private static boolean isExplicitOpenPath(GeographicalPath region) {
        return region != null
                && (region.getClosedPath() == null || !region.getClosedPath().getValue())
                && region.getDescription() != null
                && region.getDescription().getPath() != null
                && region.getDescription().getPath().getOffset() != null
                && !hasComputedLane(region);
    }

    /** Returns whether the path describes a computed lane instead of explicit nodes. */
    private static boolean hasComputedLane(GeographicalPath region) {
        return region.getDescription().getPath().getOffset().getXy() != null
                && region.getDescription().getPath().getOffset().getXy().getComputed() != null;
    }

    /** Builds the JSON Pointer prefix for a region's data-frame and region indexes. */
    private static String regionPath(DataFrameIndexes indexes) {
        return String.format(
                Locale.ROOT,
                "/value/TravelerInformation/dataFrames/%d/regions/%d",
                indexes.dataFrameIndex(),
                indexes.regionIndex());
    }

    private record LaneGeometry(
            DataFrameIndexes indexes,
            LineString centerline,
            Geometry corridor,
            String nodesPath) {
    }
}
