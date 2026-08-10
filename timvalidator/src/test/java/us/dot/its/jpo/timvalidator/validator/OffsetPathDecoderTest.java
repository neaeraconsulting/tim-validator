package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;

import us.dot.its.jpo.asn.j2735.r2024.Common.Latitude;
import us.dot.its.jpo.asn.j2735.r2024.Common.Longitude;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeListXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeOffsetPointXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeSetXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.Node_LLmD_64b;
import us.dot.its.jpo.asn.j2735.r2024.Common.Node_XY_20b;
import us.dot.its.jpo.asn.j2735.r2024.Common.Node_XY_22b;
import us.dot.its.jpo.asn.j2735.r2024.Common.Node_XY_24b;
import us.dot.its.jpo.asn.j2735.r2024.Common.Node_XY_26b;
import us.dot.its.jpo.asn.j2735.r2024.Common.Node_XY_28b;
import us.dot.its.jpo.asn.j2735.r2024.Common.Node_XY_32b;
import us.dot.its.jpo.asn.j2735.r2024.Common.OffsetLL_B18;
import us.dot.its.jpo.asn.j2735.r2024.Common.Offset_B10;
import us.dot.its.jpo.asn.j2735.r2024.Common.Offset_B11;
import us.dot.its.jpo.asn.j2735.r2024.Common.Offset_B12;
import us.dot.its.jpo.asn.j2735.r2024.Common.Offset_B13;
import us.dot.its.jpo.asn.j2735.r2024.Common.Offset_B14;
import us.dot.its.jpo.asn.j2735.r2024.Common.Offset_B16;
import us.dot.its.jpo.asn.j2735.r2024.Common.Position3D;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.NodeLL;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.NodeListLL;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.NodeOffsetPointLL;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.NodeSetLL;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Node_LL_24B;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Node_LL_28B;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Node_LL_32B;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Node_LL_36B;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Node_LL_44B;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Node_LL_48B;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.OffsetLL_B12;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.OffsetLL_B14;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.OffsetLL_B16;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.OffsetLL_B22;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.OffsetLL_B24;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.OffsetSystem;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Zoom;

class OffsetPathDecoderTest {

    @Test
    void decodeRejectsIncompletePathContainersAndInvalidScale() {
        assertFailure(OffsetPathDecoder.decode(null), "missing geographical path", "");

        GeographicalPath region = new GeographicalPath();
        assertFailure(OffsetPathDecoder.decode(region), "missing offset path description", "/description");

        region.setDescription(new GeographicalPath.DescriptionChoice());
        assertFailure(OffsetPathDecoder.decode(region), "missing offset path description", "/description/path");

        OffsetSystem path = new OffsetSystem();
        region.getDescription().setPath(path);
        assertFailure(OffsetPathDecoder.decode(region), "missing path offset choice", "/description/path/offset");

        path.setOffset(new OffsetSystem.OffsetChoice());
        assertFailure(OffsetPathDecoder.decode(region), "absence of a supported", "/description/path/offset");

        path.setScale(new Zoom(-1));
        assertFailure(OffsetPathDecoder.decode(region), "scale outside", "/description/path/scale");

        path.setScale(new Zoom(16));
        assertFailure(OffsetPathDecoder.decode(region), "scale outside", "/description/path/scale");
    }

    @Test
    void decodeTreatsNodeListsWithoutNodesAsUnsupported() {
        OffsetSystem.OffsetChoice xyChoice = new OffsetSystem.OffsetChoice();
        xyChoice.setXy(new NodeListXY());
        assertFailure(OffsetPathDecoder.decode(region(null, path(null, xyChoice))),
                "absence of a supported", "/description/path/offset");

        OffsetSystem.OffsetChoice llChoice = new OffsetSystem.OffsetChoice();
        llChoice.setLl(new NodeListLL());
        assertFailure(OffsetPathDecoder.decode(region(anchor(0, 0), path(0, llChoice))),
                "absence of a supported", "/description/path/offset");
    }

    @Test
    void decodeXySupportsAllRelativePointSizesAndDefaultScale() {
        Node_XY_20b xy1 = new Node_XY_20b();
        xy1.setX(new Offset_B10(1));
        xy1.setY(new Offset_B10(2));
        NodeOffsetPointXY point1 = new NodeOffsetPointXY();
        point1.setNode_XY1(xy1);

        Node_XY_22b xy2 = new Node_XY_22b();
        xy2.setX(new Offset_B11(1));
        xy2.setY(new Offset_B11(2));
        NodeOffsetPointXY point2 = new NodeOffsetPointXY();
        point2.setNode_XY2(xy2);

        Node_XY_24b xy3 = new Node_XY_24b();
        xy3.setX(new Offset_B12(1));
        xy3.setY(new Offset_B12(2));
        NodeOffsetPointXY point3 = new NodeOffsetPointXY();
        point3.setNode_XY3(xy3);

        Node_XY_26b xy4 = new Node_XY_26b();
        xy4.setX(new Offset_B13(1));
        xy4.setY(new Offset_B13(2));
        NodeOffsetPointXY point4 = new NodeOffsetPointXY();
        point4.setNode_XY4(xy4);

        Node_XY_28b xy5 = new Node_XY_28b();
        xy5.setX(new Offset_B14(1));
        xy5.setY(new Offset_B14(2));
        NodeOffsetPointXY point5 = new NodeOffsetPointXY();
        point5.setNode_XY5(xy5);

        Node_XY_32b xy6 = new Node_XY_32b();
        xy6.setX(new Offset_B16(1));
        xy6.setY(new Offset_B16(2));
        NodeOffsetPointXY point6 = new NodeOffsetPointXY();
        point6.setNode_XY6(xy6);

        OffsetPathDecoder.DecodeResult result = OffsetPathDecoder.decode(region(
                null,
                xyPath(null, point1, point2, point3, point4, point5, point6)));

        assertTrue(result.decoded());
        assertEquals(6, result.path().nodes().size());
        assertEquals(6.0, result.path().nodes().getLast().getX());
        assertEquals(12.0, result.path().nodes().getLast().getY());
        assertEquals("/description/path/offset/xy/nodes", result.path().nodesPathSuffix());
    }

    @Test
    void decodeXyAppliesScaleAndContinuesFromAbsoluteLatLonPoint() {
        OffsetPathDecoder.DecodeResult result = OffsetPathDecoder.decode(region(
                anchor(0, 0),
                xyPath(1,
                        absoluteXyPoint(0, 898),
                        absoluteXyPoint(0, 1_796),
                        xy6Point(10, 20))));

        assertTrue(result.decoded());
        assertEquals(3, result.path().nodes().size());
        double absoluteX = result.path().nodes().get(1).getX();
        assertTrue(absoluteX > 1_900 && absoluteX < 2_100);
        assertEquals(absoluteX + 20, result.path().nodes().get(2).getX(), 0.01);
        assertEquals(40, result.path().nodes().get(2).getY(), 0.01);
    }

    @Test
    void decodeXyRejectsMissingMalformedAndUnsupportedNodes() {
        NodeSetXY nullNodes = new NodeSetXY();
        nullNodes.add(null);
        assertFailure(OffsetPathDecoder.decode(region(null, xyPath(0, nullNodes))),
                "node 0 having no offset", "/description/path/offset/xy/nodes/0/delta");

        NodeSetXY nodes = new NodeSetXY();
        nodes.add(new NodeXY());
        assertFailure(OffsetPathDecoder.decode(region(null, xyPath(0, nodes))),
                "node 0 having no offset", "/description/path/offset/xy/nodes/0/delta");

        assertFailure(OffsetPathDecoder.decode(region(null, xyPath(0, new NodeOffsetPointXY()))),
                "unsupported offset choice", "/description/path/offset/xy/nodes/0/delta");

        Node_XY_20b missingX = new Node_XY_20b();
        missingX.setY(new Offset_B10(1));
        NodeOffsetPointXY missingXPoint = new NodeOffsetPointXY();
        missingXPoint.setNode_XY1(missingX);
        assertFailure(OffsetPathDecoder.decode(region(null, xyPath(0, missingXPoint))),
                "unsupported offset choice", "/description/path/offset/xy/nodes/0/delta");

        Node_XY_20b missingY = new Node_XY_20b();
        missingY.setX(new Offset_B10(1));
        NodeOffsetPointXY missingYPoint = new NodeOffsetPointXY();
        missingYPoint.setNode_XY1(missingY);
        assertFailure(OffsetPathDecoder.decode(region(null, xyPath(0, missingYPoint))),
                "unsupported offset choice", "/description/path/offset/xy/nodes/0/delta");

        assertFailure(OffsetPathDecoder.decode(region(null, xyPath(0, absoluteXyPoint(0, 0)))),
                "unsupported offset choice", "/description/path/offset/xy/nodes/0/delta");

        NodeOffsetPointXY invalidAbsolute = new NodeOffsetPointXY();
        invalidAbsolute.setNode_LatLon(new Node_LLmD_64b());
        assertFailure(OffsetPathDecoder.decode(region(anchor(0, 0), xyPath(0, invalidAbsolute))),
                "invalid absolute latitude/longitude", "/description/path/offset/xy/nodes/0/delta");
    }

    @Test
    void decodeLatLonRequiresValidAnchor() {
        assertFailure(OffsetPathDecoder.decode(region(null, llPath(0, ll6Point(1, 1)))),
                "without a valid anchor", "/anchor");
        assertFailure(OffsetPathDecoder.decode(region(anchor(900_000_001, 0), llPath(0, ll6Point(1, 1)))),
                "without a valid anchor", "/anchor");
        assertFailure(OffsetPathDecoder.decode(region(anchor(-900_000_001, 0), llPath(0, ll6Point(1, 1)))),
                "without a valid anchor", "/anchor");
        assertFailure(OffsetPathDecoder.decode(region(anchor(0, 1_800_000_001L), llPath(0, ll6Point(1, 1)))),
                "without a valid anchor", "/anchor");
        assertFailure(OffsetPathDecoder.decode(region(anchor(0, -1_800_000_001L), llPath(0, ll6Point(1, 1)))),
                "without a valid anchor", "/anchor");

        Position3D missingLongitude = new Position3D();
        missingLongitude.setLat(new Latitude(0));
        assertFailure(OffsetPathDecoder.decode(region(missingLongitude, llPath(0, ll6Point(1, 1)))),
                "without a valid anchor", "/anchor");

        Position3D missingLatitude = new Position3D();
        missingLatitude.setLong_(new Longitude(0));
        assertFailure(OffsetPathDecoder.decode(region(missingLatitude, llPath(0, ll6Point(1, 1)))),
                "without a valid anchor", "/anchor");
    }

    @Test
    void decodeLatLonSupportsAllRelativePointSizesAndAbsolutePoints() {
        Node_LL_24B ll1 = new Node_LL_24B();
        ll1.setLon(new OffsetLL_B12(1));
        ll1.setLat(new OffsetLL_B12(1));
        NodeOffsetPointLL point1 = new NodeOffsetPointLL();
        point1.setNode_LL1(ll1);

        Node_LL_28B ll2 = new Node_LL_28B();
        ll2.setLon(new OffsetLL_B14(1));
        ll2.setLat(new OffsetLL_B14(1));
        NodeOffsetPointLL point2 = new NodeOffsetPointLL();
        point2.setNode_LL2(ll2);

        Node_LL_32B ll3 = new Node_LL_32B();
        ll3.setLon(new OffsetLL_B16(1));
        ll3.setLat(new OffsetLL_B16(1));
        NodeOffsetPointLL point3 = new NodeOffsetPointLL();
        point3.setNode_LL3(ll3);

        Node_LL_36B ll4 = new Node_LL_36B();
        ll4.setLon(new OffsetLL_B18(1));
        ll4.setLat(new OffsetLL_B18(1));
        NodeOffsetPointLL point4 = new NodeOffsetPointLL();
        point4.setNode_LL4(ll4);

        Node_LL_44B ll5 = new Node_LL_44B();
        ll5.setLon(new OffsetLL_B22(1));
        ll5.setLat(new OffsetLL_B22(1));
        NodeOffsetPointLL point5 = new NodeOffsetPointLL();
        point5.setNode_LL5(ll5);

        NodeOffsetPointLL point6 = ll6Point(1, 1);
        NodeOffsetPointLL absolute = new NodeOffsetPointLL();
        absolute.setNode_LatLon(absolutePoint(10, 10));

        OffsetPathDecoder.DecodeResult result = OffsetPathDecoder.decode(region(
                anchor(0, 0),
                llPath(0, point1, point2, point3, point4, point5, point6, absolute)));

        assertTrue(result.decoded());
        assertEquals(7, result.path().nodes().size());
        assertEquals("/description/path/offset/ll/nodes", result.path().nodesPathSuffix());
    }

    @Test
    void decodeLatLonNormalizesLongitudeAcrossBothDateLineDirections() {
        OffsetPathDecoder.DecodeResult east = OffsetPathDecoder.decode(region(
                anchor(0, 1_799_999_999L),
                llPath(15, ll6Point(1, 0))));
        OffsetPathDecoder.DecodeResult west = OffsetPathDecoder.decode(region(
                anchor(0, -1_799_999_999L),
                llPath(15, ll6Point(-1, 0))));

        assertTrue(east.decoded());
        assertTrue(west.decoded());
    }

    @Test
    void decodeLatLonRejectsMissingMalformedAndUnsupportedNodes() {
        NodeSetLL nullNodes = new NodeSetLL();
        nullNodes.add(null);
        assertFailure(OffsetPathDecoder.decode(region(anchor(0, 0), llPath(0, nullNodes))),
                "node 0 having no offset", "/description/path/offset/ll/nodes/0/delta");

        NodeSetLL nodes = new NodeSetLL();
        nodes.add(new NodeLL());
        assertFailure(OffsetPathDecoder.decode(region(anchor(0, 0), llPath(0, nodes))),
                "node 0 having no offset", "/description/path/offset/ll/nodes/0/delta");

        assertFailure(OffsetPathDecoder.decode(region(anchor(0, 0), llPath(0, new NodeOffsetPointLL()))),
                "invalid or unsupported offset", "/description/path/offset/ll/nodes/0/delta");

        Node_LL_24B missingLongitude = new Node_LL_24B();
        missingLongitude.setLat(new OffsetLL_B12(1));
        NodeOffsetPointLL missingLongitudePoint = new NodeOffsetPointLL();
        missingLongitudePoint.setNode_LL1(missingLongitude);
        assertFailure(OffsetPathDecoder.decode(region(anchor(0, 0), llPath(0, missingLongitudePoint))),
                "invalid or unsupported offset", "/description/path/offset/ll/nodes/0/delta");

        Node_LL_24B missingLatitude = new Node_LL_24B();
        missingLatitude.setLon(new OffsetLL_B12(1));
        NodeOffsetPointLL missingLatitudePoint = new NodeOffsetPointLL();
        missingLatitudePoint.setNode_LL1(missingLatitude);
        assertFailure(OffsetPathDecoder.decode(region(anchor(0, 0), llPath(0, missingLatitudePoint))),
                "invalid or unsupported offset", "/description/path/offset/ll/nodes/0/delta");

        assertFailure(OffsetPathDecoder.decode(region(
                        anchor(899_999_999L, 0),
                        llPath(15, ll6Point(0, 1)))),
                "invalid or unsupported offset", "/description/path/offset/ll/nodes/0/delta");

        NodeOffsetPointLL invalidAbsolute = new NodeOffsetPointLL();
        invalidAbsolute.setNode_LatLon(new Node_LLmD_64b());
        assertFailure(OffsetPathDecoder.decode(region(anchor(0, 0), llPath(0, invalidAbsolute))),
                "invalid or unsupported offset", "/description/path/offset/ll/nodes/0/delta");
    }

    @Test
    void decodedPathDefensivelyCopiesItsNodeList() {
        List<Coordinate> mutable = new ArrayList<>();
        mutable.add(new CoordinateXY(1, 2));

        OffsetPathDecoder.DecodedPath path = new OffsetPathDecoder.DecodedPath(mutable, "/nodes");
        mutable.clear();

        assertEquals(1, path.nodes().size());
    }

    private static void assertFailure(
            OffsetPathDecoder.DecodeResult result,
            String reasonPart,
            String path) {
        assertFalse(result.decoded());
        assertTrue(result.failureReason().contains(reasonPart), result.failureReason());
        assertEquals(path, result.failurePathSuffix());
    }

    private static GeographicalPath region(Position3D anchor, GeographicalPath.DescriptionChoice description) {
        GeographicalPath region = new GeographicalPath();
        region.setAnchor(anchor);
        region.setDescription(description);
        return region;
    }

    private static Position3D anchor(long latitude, long longitude) {
        Position3D anchor = new Position3D();
        anchor.setLat(new Latitude(latitude));
        anchor.setLong_(new Longitude(longitude));
        return anchor;
    }

    private static GeographicalPath.DescriptionChoice xyPath(Integer scale, NodeOffsetPointXY... points) {
        NodeSetXY nodes = new NodeSetXY();
        for (NodeOffsetPointXY point : points) {
            nodes.add(xyNode(point));
        }
        return xyPath(scale, nodes);
    }

    private static GeographicalPath.DescriptionChoice xyPath(Integer scale, NodeSetXY nodes) {
        NodeListXY list = new NodeListXY();
        list.setNodes(nodes);
        OffsetSystem.OffsetChoice choice = new OffsetSystem.OffsetChoice();
        choice.setXy(list);
        return path(scale, choice);
    }

    private static GeographicalPath.DescriptionChoice llPath(int scale, NodeOffsetPointLL... points) {
        NodeSetLL nodes = new NodeSetLL();
        for (NodeOffsetPointLL point : points) {
            NodeLL node = new NodeLL();
            node.setDelta(point);
            nodes.add(node);
        }
        return llPath(scale, nodes);
    }

    private static GeographicalPath.DescriptionChoice llPath(int scale, NodeSetLL nodes) {
        NodeListLL list = new NodeListLL();
        list.setNodes(nodes);
        OffsetSystem.OffsetChoice choice = new OffsetSystem.OffsetChoice();
        choice.setLl(list);
        return path(scale, choice);
    }

    private static GeographicalPath.DescriptionChoice path(
            Integer scale,
            OffsetSystem.OffsetChoice choice) {
        OffsetSystem path = new OffsetSystem();
        if (scale != null) {
            path.setScale(new Zoom(scale));
        }
        path.setOffset(choice);
        GeographicalPath.DescriptionChoice description = new GeographicalPath.DescriptionChoice();
        description.setPath(path);
        return description;
    }

    private static NodeXY xyNode(NodeOffsetPointXY point) {
        NodeXY node = new NodeXY();
        node.setDelta(point);
        return node;
    }

    private static NodeOffsetPointXY xy6Point(long x, long y) {
        Node_XY_32b value = new Node_XY_32b();
        value.setX(new Offset_B16(x));
        value.setY(new Offset_B16(y));
        NodeOffsetPointXY point = new NodeOffsetPointXY();
        point.setNode_XY6(value);
        return point;
    }

    private static NodeOffsetPointXY absoluteXyPoint(long latitude, long longitude) {
        NodeOffsetPointXY point = new NodeOffsetPointXY();
        point.setNode_LatLon(absolutePoint(latitude, longitude));
        return point;
    }

    private static Node_LLmD_64b absolutePoint(long latitude, long longitude) {
        Node_LLmD_64b point = new Node_LLmD_64b();
        point.setLat(new Latitude(latitude));
        point.setLon(new Longitude(longitude));
        return point;
    }

    private static NodeOffsetPointLL ll6Point(long longitude, long latitude) {
        Node_LL_48B value = new Node_LL_48B();
        value.setLon(new OffsetLL_B24(longitude));
        value.setLat(new OffsetLL_B24(latitude));
        NodeOffsetPointLL point = new NodeOffsetPointLL();
        point.setNode_LL6(value);
        return point;
    }
}
