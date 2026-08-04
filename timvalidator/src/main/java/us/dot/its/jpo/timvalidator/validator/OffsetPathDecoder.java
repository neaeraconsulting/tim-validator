package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.Proj4jException;
import org.locationtech.proj4j.ProjCoordinate;

import us.dot.its.jpo.asn.j2735.r2024.Common.NodeListXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeOffsetPointXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeSetXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.Node_LLmD_64b;
import us.dot.its.jpo.asn.j2735.r2024.Common.Position3D;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.NodeLL;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.NodeListLL;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.NodeOffsetPointLL;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.NodeSetLL;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.OffsetSystem;
import us.dot.its.jpo.asn.runtime.types.Asn1Integer;

/**
 * Decodes a J2735 offset path into planar JTS coordinates measured in centimeters.
 *
 * <p>JTS operates on planar coordinates, so latitude/longitude nodes are projected
 * into a WGS-84 Transverse Mercator coordinate system centered on the path anchor.
 * Using one local projection for the complete path preserves the relative geometry
 * needed by the best-practices checks.</p>
 */
final class OffsetPathDecoder {

    private static final double DEGREES_PER_J2735_UNIT = 1e-7;
    private static final double CENTIMETERS_PER_METER = 100.0;
    private static final CRSFactory CRS_FACTORY = new CRSFactory();
    private static final CoordinateTransformFactory TRANSFORM_FACTORY = new CoordinateTransformFactory();
    private static final CoordinateReferenceSystem WGS84 = CRS_FACTORY.createFromParameters(
            "WGS84",
            "+proj=longlat +datum=WGS84 +no_defs");
    // Proj4J's tmerc implementation does not accept modern PROJ's +approx option.
    private static final String LOCAL_TRANSVERSE_MERCATOR_PARAMETERS =
            "+proj=tmerc +lon_0=%.10f +lat_0=%.10f +k_0=1 "
                    + "+x_0=0 +y_0=0 +ellps=WGS84 +datum=WGS84 +units=cm +no_defs";

    private OffsetPathDecoder() {
    }

    static DecodeResult decode(GeographicalPath region) {
        try {
            return decodePath(region);
        } catch (RuntimeException exception) {
            return DecodeResult.failure("a coordinate decoding failure", "/description/path");
        }
    }

    private static DecodeResult decodePath(GeographicalPath region) {
        if (region == null) {
            return DecodeResult.failure("a missing geographical path", "");
        }
        if (region.getDescription() == null) {
            return DecodeResult.failure("a missing offset path description", "/description");
        }
        if (region.getDescription().getPath() == null) {
            return DecodeResult.failure("a missing offset path description", "/description/path");
        }

        OffsetSystem path = region.getDescription().getPath();
        if (path.getOffset() == null) {
            return DecodeResult.failure("a missing path offset choice", "/description/path/offset");
        }

        double scale = offsetScale(path);
        if (!Double.isFinite(scale)) {
            return DecodeResult.failure(
                    "a path scale outside the supported range 0 through 15",
                    "/description/path/scale");
        }

        Optional<ProjCoordinate> anchor = geographicCoordinate(region.getAnchor());
        OffsetSystem.OffsetChoice offset = path.getOffset();
        NodeListXY xy = offset.getXy();
        if (xy != null && xy.getNodes() != null) {
            return decodeXy(xy.getNodes(), anchor, scale);
        }

        NodeListLL ll = offset.getLl();
        if (ll != null && ll.getNodes() != null) {
            if (anchor.isEmpty()) {
                return DecodeResult.failure(
                        "a latitude/longitude path without a valid anchor",
                        "/anchor");
            }
            ProjCoordinate origin = anchor.orElseThrow();
            return decodeLatLon(
                    ll.getNodes(),
                    origin,
                    localTransform(origin),
                    scale);
        }

        return DecodeResult.failure(
                "the absence of a supported XY or latitude/longitude node list",
                "/description/path/offset");
    }

    /**
     * Projects WGS-84 points into an origin-centered local coordinate system in meters.
     *
     * <p>The path decoder and roadway-heading validator share this projection so their
     * planar distance and angle calculations use the same WGS-84 behavior.</p>
     */
    static Optional<List<Coordinate>> displacementsMeters(
            Coordinate origin,
            Coordinate[] points) {
        if (origin == null || points == null) {
            return Optional.empty();
        }

        Optional<ProjCoordinate> geographicOrigin =
                geographicCoordinate(origin.getX(), origin.getY());
        if (geographicOrigin.isEmpty()) {
            return Optional.empty();
        }

        CoordinateTransform transform = localTransform(geographicOrigin.orElseThrow());
        Optional<Coordinate> projectedOrigin =
                project(transform, geographicOrigin.orElseThrow());
        if (projectedOrigin.isEmpty()) {
            return Optional.empty();
        }

        Coordinate center = projectedOrigin.orElseThrow();
        List<Coordinate> displacements = new ArrayList<>(points.length);
        for (Coordinate point : points) {
            if (point == null) {
                return Optional.empty();
            }

            Optional<ProjCoordinate> geographicPoint =
                    geographicCoordinate(point.getX(), point.getY());
            if (geographicPoint.isEmpty()) {
                return Optional.empty();
            }

            Optional<Coordinate> projected =
                    project(transform, geographicPoint.orElseThrow());
            if (projected.isEmpty()) {
                return Optional.empty();
            }

            Coordinate localPoint = projected.orElseThrow();
            displacements.add(new CoordinateXY(
                    (localPoint.getX() - center.getX()) / CENTIMETERS_PER_METER,
                    (localPoint.getY() - center.getY()) / CENTIMETERS_PER_METER));
        }

        return Optional.of(List.copyOf(displacements));
    }

    /**
     * Converts a J2735 position into a validated WGS-84 JTS coordinate in decimal degrees.
     * The coordinate's x value is longitude and its y value is latitude.
     */
    static Optional<Coordinate> wgs84Coordinate(Position3D position) {
        return geographicCoordinate(position)
                .map(coordinate -> new CoordinateXY(coordinate.x, coordinate.y));
    }

    /**
     * Converts anchor-relative local coordinates in meters back to WGS-84.
     */
    static Optional<List<Coordinate>> wgs84Coordinates(
            Position3D origin,
            Coordinate[] localPointsMeters) {
        if (localPointsMeters == null) {
            return Optional.empty();
        }

        Optional<ProjCoordinate> geographicOrigin = geographicCoordinate(origin);
        if (geographicOrigin.isEmpty()) {
            return Optional.empty();
        }

        ProjCoordinate anchor = geographicOrigin.orElseThrow();
        CoordinateReferenceSystem local = localCoordinateReferenceSystem(anchor);
        CoordinateTransform forward = TRANSFORM_FACTORY.createTransform(WGS84, local);
        CoordinateTransform inverse = TRANSFORM_FACTORY.createTransform(local, WGS84);
        Optional<Coordinate> projectedOrigin = project(forward, anchor);
        if (projectedOrigin.isEmpty()) {
            return Optional.empty();
        }

        Coordinate projectedAnchor = projectedOrigin.orElseThrow();
        List<Coordinate> geographicCoordinates =
                new ArrayList<>(localPointsMeters.length);
        for (Coordinate localPoint : localPointsMeters) {
            if (localPoint == null || !localPoint.isValid()) {
                return Optional.empty();
            }

            ProjCoordinate projectedPoint = new ProjCoordinate(
                    projectedAnchor.getX()
                            + localPoint.getX() * CENTIMETERS_PER_METER,
                    projectedAnchor.getY()
                            + localPoint.getY() * CENTIMETERS_PER_METER);
            Optional<Coordinate> geographicPoint = project(inverse, projectedPoint);
            if (geographicPoint.isEmpty()) {
                return Optional.empty();
            }

            Coordinate coordinate = geographicPoint.orElseThrow();
            Optional<ProjCoordinate> validated =
                    geographicCoordinate(coordinate.getX(), coordinate.getY());
            if (validated.isEmpty()) {
                return Optional.empty();
            }
            ProjCoordinate value = validated.orElseThrow();
            geographicCoordinates.add(new CoordinateXY(value.x, value.y));
        }
        return Optional.of(List.copyOf(geographicCoordinates));
    }

    private static DecodeResult decodeXy(
            NodeSetXY nodes,
            Optional<ProjCoordinate> anchor,
            double scale) {
        List<Coordinate> coordinates = new ArrayList<>(nodes.size());
        Coordinate current = new CoordinateXY(0.0, 0.0);
        CoordinateTransform projection = null;

        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            NodeXY node = nodes.get(nodeIndex);
            if (node == null || node.getDelta() == null) {
                return DecodeResult.failure(
                        "XY path node " + nodeIndex + " having no offset",
                        xyNodePath(nodeIndex));
            }

            NodeOffsetPointXY encodedPoint = node.getDelta();
            Coordinate offset = xyOffset(encodedPoint);
            if (offset != null) {
                current = new CoordinateXY(
                        current.getX() + offset.getX() * scale,
                        current.getY() + offset.getY() * scale);
            } else if (encodedPoint.getNode_LatLon() != null && anchor.isPresent()) {
                Optional<ProjCoordinate> absolute = geographicCoordinate(encodedPoint.getNode_LatLon());
                if (absolute.isEmpty()) {
                    return DecodeResult.failure(
                            "XY path node " + nodeIndex + " having an invalid absolute latitude/longitude",
                            xyNodePath(nodeIndex));
                }
                if (projection == null) {
                    projection = localTransform(anchor.orElseThrow());
                }
                ProjCoordinate absolutePoint = absolute.orElseThrow();
                Optional<Coordinate> projected = project(projection, absolutePoint);
                if (projected.isEmpty()) {
                    return DecodeResult.failure(
                            "XY path node " + nodeIndex + " failing projection relative to the anchor",
                            xyNodePath(nodeIndex));
                }
                current = projected.orElseThrow();
            } else {
                // A regional or otherwise unsupported choice cannot be decoded safely.
                return DecodeResult.failure(
                        "XY path node " + nodeIndex + " using an unsupported offset choice",
                        xyNodePath(nodeIndex));
            }

            coordinates.add(current.copy());
        }

        return DecodeResult.success(coordinates, "/description/path/offset/xy/nodes");
    }

    private static DecodeResult decodeLatLon(
            NodeSetLL nodes,
            ProjCoordinate anchor,
            CoordinateTransform localTransform,
            double scale) {
        List<Coordinate> coordinates = new ArrayList<>(nodes.size());
        ProjCoordinate currentGeographic = anchor;

        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            NodeLL node = nodes.get(nodeIndex);
            if (node == null || node.getDelta() == null) {
                return DecodeResult.failure(
                        "latitude/longitude path node " + nodeIndex + " having no offset",
                        llNodePath(nodeIndex));
            }

            NodeOffsetPointLL encodedPoint = node.getDelta();
            Optional<ProjCoordinate> nextGeographic =
                    nextLatLonPoint(encodedPoint, currentGeographic, scale);
            if (nextGeographic.isEmpty()) {
                return DecodeResult.failure(
                        "latitude/longitude path node " + nodeIndex + " having an invalid or unsupported offset",
                        llNodePath(nodeIndex));
            }

            ProjCoordinate next = nextGeographic.orElseThrow();
            Optional<Coordinate> projected = project(localTransform, next);
            if (projected.isEmpty()) {
                return DecodeResult.failure(
                        "latitude/longitude path node " + nodeIndex + " failing projection relative to the anchor",
                        llNodePath(nodeIndex));
            }
            coordinates.add(projected.orElseThrow());
            currentGeographic = next;
        }

        return DecodeResult.success(coordinates, "/description/path/offset/ll/nodes");
    }

    private static String xyNodePath(int nodeIndex) {
        return "/description/path/offset/xy/nodes/" + nodeIndex + "/delta";
    }

    private static String llNodePath(int nodeIndex) {
        return "/description/path/offset/ll/nodes/" + nodeIndex + "/delta";
    }

    private static double offsetScale(OffsetSystem path) {
        if (path.getScale() == null) {
            return 1.0;
        }

        long zoom = path.getScale().getValue();
        if (zoom < 0 || zoom > 15) {
            return Double.NaN;
        }
        return Math.scalb(1.0, (int) zoom);
    }

    private static Coordinate xyOffset(NodeOffsetPointXY point) {
        if (point.getNode_XY1() != null) {
            return coordinate(point.getNode_XY1().getX(), point.getNode_XY1().getY());
        }
        if (point.getNode_XY2() != null) {
            return coordinate(point.getNode_XY2().getX(), point.getNode_XY2().getY());
        }
        if (point.getNode_XY3() != null) {
            return coordinate(point.getNode_XY3().getX(), point.getNode_XY3().getY());
        }
        if (point.getNode_XY4() != null) {
            return coordinate(point.getNode_XY4().getX(), point.getNode_XY4().getY());
        }
        if (point.getNode_XY5() != null) {
            return coordinate(point.getNode_XY5().getX(), point.getNode_XY5().getY());
        }
        if (point.getNode_XY6() != null) {
            return coordinate(point.getNode_XY6().getX(), point.getNode_XY6().getY());
        }
        return null;
    }

    private static Optional<ProjCoordinate> nextLatLonPoint(
            NodeOffsetPointLL point,
            ProjCoordinate previous,
            double scale) {
        if (point.getNode_LL1() != null) {
            return relativePoint(previous, point.getNode_LL1().getLon(), point.getNode_LL1().getLat(), scale);
        }
        if (point.getNode_LL2() != null) {
            return relativePoint(previous, point.getNode_LL2().getLon(), point.getNode_LL2().getLat(), scale);
        }
        if (point.getNode_LL3() != null) {
            return relativePoint(previous, point.getNode_LL3().getLon(), point.getNode_LL3().getLat(), scale);
        }
        if (point.getNode_LL4() != null) {
            return relativePoint(previous, point.getNode_LL4().getLon(), point.getNode_LL4().getLat(), scale);
        }
        if (point.getNode_LL5() != null) {
            return relativePoint(previous, point.getNode_LL5().getLon(), point.getNode_LL5().getLat(), scale);
        }
        if (point.getNode_LL6() != null) {
            return relativePoint(previous, point.getNode_LL6().getLon(), point.getNode_LL6().getLat(), scale);
        }
        if (point.getNode_LatLon() != null) {
            return geographicCoordinate(point.getNode_LatLon());
        }
        return Optional.empty();
    }

    private static Optional<ProjCoordinate> relativePoint(
            ProjCoordinate previous,
            Asn1Integer longitudeOffset,
            Asn1Integer latitudeOffset,
            double scale) {
        if (longitudeOffset == null || latitudeOffset == null) {
            return Optional.empty();
        }

        double longitude = normalizeLongitude(previous.x + offsetDegrees(longitudeOffset, scale));
        double latitude = previous.y + offsetDegrees(latitudeOffset, scale);
        return geographicCoordinate(longitude, latitude);
    }

    private static double offsetDegrees(Asn1Integer offset, double scale) {
        return offset.getValue() * scale * DEGREES_PER_J2735_UNIT;
    }

    private static Coordinate coordinate(Asn1Integer x, Asn1Integer y) {
        if (x == null || y == null) {
            return null;
        }
        return new CoordinateXY(x.getValue(), y.getValue());
    }

    private static Optional<ProjCoordinate> geographicCoordinate(Position3D position) {
        if (position == null) {
            return Optional.empty();
        }
        return geographicCoordinate(position.getLong_(), position.getLat());
    }

    private static Optional<ProjCoordinate> geographicCoordinate(Node_LLmD_64b point) {
        if (point == null) {
            return Optional.empty();
        }
        return geographicCoordinate(point.getLon(), point.getLat());
    }

    private static Optional<ProjCoordinate> geographicCoordinate(
            Asn1Integer longitude,
            Asn1Integer latitude) {
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }
        return geographicCoordinate(
                longitude.getValue() * DEGREES_PER_J2735_UNIT,
                latitude.getValue() * DEGREES_PER_J2735_UNIT);
    }

    /** Creates a geographic coordinate with X as longitude and Y as latitude, in decimal degrees. */
    private static Optional<ProjCoordinate> geographicCoordinate(
            double longitudeDegrees,
            double latitudeDegrees) {
        if (!Double.isFinite(longitudeDegrees) || !Double.isFinite(latitudeDegrees)
                || longitudeDegrees < -180.0 || longitudeDegrees > 180.0
                || latitudeDegrees < -90.0 || latitudeDegrees > 90.0) {
            return Optional.empty();
        }
        return Optional.of(new ProjCoordinate(longitudeDegrees, latitudeDegrees));
    }

    private static CoordinateTransform localTransform(ProjCoordinate anchor) {
        return TRANSFORM_FACTORY.createTransform(
                WGS84,
                localCoordinateReferenceSystem(anchor));
    }

    private static CoordinateReferenceSystem localCoordinateReferenceSystem(
            ProjCoordinate anchor) {
        String parameters = String.format(
                Locale.ROOT,
                LOCAL_TRANSVERSE_MERCATOR_PARAMETERS,
                anchor.x,
                anchor.y);
        return CRS_FACTORY.createFromParameters(
                "TIM local Transverse Mercator",
                parameters);
    }

    private static Optional<Coordinate> project(
            CoordinateTransform localTransform,
            ProjCoordinate point) {
        try {
            ProjCoordinate projected = localTransform.transform(
                    point,
                    new ProjCoordinate());
            if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y)) {
                return Optional.empty();
            }
            return Optional.of(new CoordinateXY(projected.x, projected.y));
        } catch (Proj4jException exception) {
            return Optional.empty();
        }
    }

    private static double normalizeLongitude(double longitudeDegrees) {
        double normalized = longitudeDegrees % 360.0;
        if (normalized > 180.0) {
            return normalized - 360.0;
        }
        if (normalized < -180.0) {
            return normalized + 360.0;
        }
        return normalized;
    }

    record DecodedPath(List<Coordinate> nodes, String nodesPathSuffix) {
        DecodedPath {
            nodes = List.copyOf(nodes);
        }
    }

    record DecodeResult(DecodedPath path, String failureReason, String failurePathSuffix) {
        static DecodeResult success(List<Coordinate> nodes, String nodesPathSuffix) {
            return new DecodeResult(new DecodedPath(nodes, nodesPathSuffix), null, null);
        }

        static DecodeResult failure(String reason, String pathSuffix) {
            return new DecodeResult(null, reason, pathSuffix);
        }

        boolean decoded() {
            return path != null;
        }
    }

}
