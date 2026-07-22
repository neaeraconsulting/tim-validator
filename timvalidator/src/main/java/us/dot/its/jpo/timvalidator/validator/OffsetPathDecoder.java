package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.locationtech.jts.geom.Coordinate;

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
 * <p>JTS operates on planar coordinates, so latitude/longitude nodes are converted
 * one segment at a time to a local WGS-84 east/north displacement. Accumulating
 * those local displacements avoids treating longitude degrees as a fixed distance
 * and preserves the bend geometry needed by the best-practices checks.</p>
 */
final class OffsetPathDecoder {

    private static final double COORDINATE_UNITS_PER_DEGREE = 10_000_000.0;
    private static final double CENTIMETERS_PER_METER = 100.0;

    // WGS-84 ellipsoid parameters.
    private static final double WGS84_SEMI_MAJOR_AXIS_METERS = 6_378_137.0;
    private static final double WGS84_ECCENTRICITY_SQUARED = 6.694_379_990_14e-3;

    private OffsetPathDecoder() {
    }

    static Optional<DecodedPath> decode(GeographicalPath region) {
        if (region == null || region.getDescription() == null || region.getDescription().getPath() == null) {
            return Optional.empty();
        }

        OffsetSystem path = region.getDescription().getPath();
        if (path.getOffset() == null) {
            return Optional.empty();
        }

        double scale = offsetScale(path);
        if (!Double.isFinite(scale)) {
            return Optional.empty();
        }

        OffsetSystem.OffsetChoice offset = path.getOffset();
        NodeListXY xy = offset.getXy();
        if (xy != null && xy.getNodes() != null) {
            return decodeXy(xy.getNodes(), geographicPoint(region.getAnchor()), scale);
        }

        NodeListLL ll = offset.getLl();
        if (ll != null && ll.getNodes() != null) {
            return decodeLatLon(ll.getNodes(), geographicPoint(region.getAnchor()), scale);
        }

        return Optional.empty();
    }

    private static Optional<DecodedPath> decodeXy(
            NodeSetXY nodes,
            Optional<GeographicPoint> anchor,
            double scale) {
        List<Coordinate> coordinates = new ArrayList<>(nodes.size());
        Coordinate current = new Coordinate(0.0, 0.0);

        for (NodeXY node : nodes) {
            if (node == null || node.getDelta() == null) {
                return Optional.empty();
            }

            NodeOffsetPointXY encodedPoint = node.getDelta();
            Coordinate offset = xyOffset(encodedPoint);
            if (offset != null) {
                current = new Coordinate(
                        current.getX() + offset.getX() * scale,
                        current.getY() + offset.getY() * scale);
            } else if (encodedPoint.getNode_LatLon() != null && anchor.isPresent()) {
                Optional<GeographicPoint> absolute = geographicPoint(encodedPoint.getNode_LatLon());
                if (absolute.isEmpty()) {
                    return Optional.empty();
                }
                current = displacementCm(anchor.orElseThrow(), absolute.orElseThrow());
            } else {
                // A regional or otherwise unsupported choice cannot be decoded safely.
                return Optional.empty();
            }

            coordinates.add(current.copy());
        }

        return Optional.of(new DecodedPath(coordinates));
    }

    private static Optional<DecodedPath> decodeLatLon(
            NodeSetLL nodes,
            Optional<GeographicPoint> anchor,
            double scale) {
        if (anchor.isEmpty()) {
            return Optional.empty();
        }

        List<Coordinate> coordinates = new ArrayList<>(nodes.size());
        GeographicPoint currentGeographic = anchor.orElseThrow();
        Coordinate currentPlanar = new Coordinate(0.0, 0.0);

        for (NodeLL node : nodes) {
            if (node == null || node.getDelta() == null) {
                return Optional.empty();
            }

            NodeOffsetPointLL encodedPoint = node.getDelta();
            Optional<GeographicPoint> nextGeographic = nextLatLonPoint(encodedPoint, currentGeographic, scale);
            if (nextGeographic.isEmpty()) {
                return Optional.empty();
            }

            Coordinate segment = displacementCm(currentGeographic, nextGeographic.orElseThrow());
            currentPlanar = new Coordinate(
                    currentPlanar.getX() + segment.getX(),
                    currentPlanar.getY() + segment.getY());
            coordinates.add(currentPlanar.copy());
            currentGeographic = nextGeographic.orElseThrow();
        }

        return Optional.of(new DecodedPath(coordinates));
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

    private static Optional<GeographicPoint> nextLatLonPoint(
            NodeOffsetPointLL point,
            GeographicPoint previous,
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
            return geographicPoint(point.getNode_LatLon());
        }
        return Optional.empty();
    }

    private static Optional<GeographicPoint> relativePoint(
            GeographicPoint previous,
            Asn1Integer longitudeOffset,
            Asn1Integer latitudeOffset,
            double scale) {
        if (longitudeOffset == null || latitudeOffset == null) {
            return Optional.empty();
        }

        double longitude = normalizeLongitude(previous.longitudeDegrees()
                + longitudeOffset.getValue() * scale / COORDINATE_UNITS_PER_DEGREE);
        double latitude = previous.latitudeDegrees()
                + latitudeOffset.getValue() * scale / COORDINATE_UNITS_PER_DEGREE;
        return GeographicPoint.create(latitude, longitude);
    }

    private static Coordinate coordinate(Asn1Integer x, Asn1Integer y) {
        if (x == null || y == null) {
            return null;
        }
        return new Coordinate(x.getValue(), y.getValue());
    }

    private static Optional<GeographicPoint> geographicPoint(Position3D position) {
        if (position == null) {
            return Optional.empty();
        }
        return geographicPoint(position.getLat(), position.getLong_());
    }

    private static Optional<GeographicPoint> geographicPoint(Node_LLmD_64b point) {
        if (point == null) {
            return Optional.empty();
        }
        return geographicPoint(point.getLat(), point.getLon());
    }

    private static Optional<GeographicPoint> geographicPoint(Asn1Integer latitude, Asn1Integer longitude) {
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }
        return GeographicPoint.create(
                latitude.getValue() / COORDINATE_UNITS_PER_DEGREE,
                longitude.getValue() / COORDINATE_UNITS_PER_DEGREE);
    }

    private static Coordinate displacementCm(GeographicPoint from, GeographicPoint to) {
        double meanLatitudeRadians = Math.toRadians(
                (from.latitudeDegrees() + to.latitudeDegrees()) / 2.0);
        double sinLatitude = Math.sin(meanLatitudeRadians);
        double w = Math.sqrt(1.0 - WGS84_ECCENTRICITY_SQUARED * sinLatitude * sinLatitude);
        double primeVerticalRadius = WGS84_SEMI_MAJOR_AXIS_METERS / w;
        double meridionalRadius = WGS84_SEMI_MAJOR_AXIS_METERS
                * (1.0 - WGS84_ECCENTRICITY_SQUARED) / (w * w * w);

        double latitudeDeltaRadians = Math.toRadians(to.latitudeDegrees() - from.latitudeDegrees());
        double longitudeDeltaRadians = Math.toRadians(normalizeLongitude(
                to.longitudeDegrees() - from.longitudeDegrees()));

        double eastCm = longitudeDeltaRadians * primeVerticalRadius
                * Math.cos(meanLatitudeRadians) * CENTIMETERS_PER_METER;
        double northCm = latitudeDeltaRadians * meridionalRadius * CENTIMETERS_PER_METER;
        return new Coordinate(eastCm, northCm);
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

    record DecodedPath(List<Coordinate> nodes) {
        DecodedPath {
            nodes = List.copyOf(nodes);
        }
    }

    private record GeographicPoint(double latitudeDegrees, double longitudeDegrees) {
        static Optional<GeographicPoint> create(double latitudeDegrees, double longitudeDegrees) {
            if (!Double.isFinite(latitudeDegrees) || !Double.isFinite(longitudeDegrees)
                    || latitudeDegrees < -90.0 || latitudeDegrees > 90.0
                    || longitudeDegrees < -180.0 || longitudeDegrees > 180.0) {
                return Optional.empty();
            }
            return Optional.of(new GeographicPoint(latitudeDegrees, longitudeDegrees));
        }
    }
}
