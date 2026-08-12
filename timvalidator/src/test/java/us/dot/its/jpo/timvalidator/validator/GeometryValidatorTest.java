package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import us.dot.its.jpo.asn.j2735.r2024.Common.Elevation;
import us.dot.its.jpo.asn.j2735.r2024.Common.HeadingSlice;
import us.dot.its.jpo.asn.j2735.r2024.Common.LaneWidth;
import us.dot.its.jpo.asn.j2735.r2024.Common.Latitude;
import us.dot.its.jpo.asn.j2735.r2024.Common.Longitude;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeListXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeOffsetPointXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeSetXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.Node_LLmD_64b;
import us.dot.its.jpo.asn.j2735.r2024.Common.Node_XY_32b;
import us.dot.its.jpo.asn.j2735.r2024.Common.Offset_B16;
import us.dot.its.jpo.asn.j2735.r2024.Common.Position3D;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Circle;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeometricProjection;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.NodeLL;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.NodeListLL;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.NodeOffsetPointLL;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.NodeSetLL;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Node_LL_48B;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.OffsetLL_B24;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.OffsetSystem;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrameList;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Zoom;
import us.dot.its.jpo.asn.runtime.types.Asn1Boolean;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;

class GeometryValidatorTest {

    private static final String REGION_PATH =
            "/value/TravelerInformation/dataFrames/0/regions/0";
    private static final String XY_NODES_PATH =
            REGION_PATH + "/description/path/offset/xy/nodes";

    @Test
    void validate_circleDoesNotReportMissingOffsetPathWarning() {
        GeometricProjection geometry = new GeometricProjection();
        geometry.setCircle(new Circle());
        GeographicalPath.DescriptionChoice description =
                new GeographicalPath.DescriptionChoice();
        description.setGeometry(geometry);
        TravelerInformationMessageFrame message = message(region(
                anchor(337_545_852L, -843_986_600L),
                description));

        List<ValidationIssue> issues = validateStructured(message);

        assertFalse(issues.stream().anyMatch(issue ->
                issue.message().contains("missing offset path description")));
    }

    @Test
    void validate_emptyPathReportsGeometryNotEvaluatedWarning() {
        TravelerInformationMessageFrame message = xyMessage(0L, 0);

        List<ValidationIssue> issues = validateStructured(message);

        assertTrue(issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.WARNING
                && issue.message().contains("geometry not evaluated due to an empty path")));
        assertEquals(XY_NODES_PATH, findIssue(issues, "empty path").path());
    }

    @Test
    void validate_undecodablePathReportsReasonAsWarning() {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(new NodeOffsetPointXY()));

        List<ValidationIssue> issues = validateStructured(message);

        assertTrue(issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.WARNING
                && issue.message().contains("geometry not evaluated due to")
                && issue.message().contains("unsupported offset choice")));
        assertEquals(XY_NODES_PATH + "/0/delta",
                findIssue(issues, "unsupported offset choice").path());
    }

    @Test
    void validate_rightAngleLaneWidthWithinLimitDoesNotReportGeometryIssue() {
        TravelerInformationMessageFrame message = xyMessage(15_000L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(0L, 10_000L));

        List<String> issues = validate(message);

        assertFalse(containsLaneWidthIssue(issues),
                "A 150 meter centered width should fit a right-angle bend with 100 meter segments");
        assertFalse(containsLaneCorridorIssue(issues));
    }

    @Test
    void validate_laneWidthExactlyAtBendLimitDoesNotReportBendWidthIssue() {
        TravelerInformationMessageFrame message = xyMessage(20_000L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(0L, 10_000L));

        assertFalse(containsLaneWidthIssue(validate(message)));
    }

    @Test
    void validate_laneWidthBeyondLimitReportsGeometryIssue() {
        TravelerInformationMessageFrame message = xyMessage(25_000L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(0L, 10_000L));

        assertTrue(containsLaneWidthIssue(validate(message)));
        assertEquals(REGION_PATH + "/laneWidth",
                findIssue(validateStructured(message), "exceeds maximum allowable").path());
    }

    @Test
    void validate_twoBendsSharingSegmentAreEvaluatedIndependently() {
        TravelerInformationMessageFrame message = xyMessage(15_000L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(0L, 10_000L),
                xyNode(-10_000L, 0L));

        assertFalse(containsLaneWidthIssue(validate(message)),
                "Each three-point bend independently allows a 200 meter centered width");
    }

    @Test
    void validate_laneCorridorWithOverlappingNonAdjacentSectionsReportsIssue() {
        TravelerInformationMessageFrame message = xyMessage(15_000L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(0L, 10_000L),
                xyNode(-10_000L, 0L));

        List<String> issues = validate(message);

        assertFalse(containsLaneWidthIssue(issues),
                "Each individual bend remains within the local bend-width limit");
        assertTrue(containsLaneCorridorIssue(issues),
                "The complete corridor must detect overlap between the two parallel sections");
        assertEquals(XY_NODES_PATH,
                findIssue(validateStructured(message), "lane corridor").path());
    }

    @Test
    void validate_straightPathDoesNotImposeLaneWidthLimit() {
        TravelerInformationMessageFrame message = xyMessage(32_767L, 0,
                xyNode(1_000L, 0L),
                xyNode(100L, 0L),
                xyNode(100L, 0L));

        assertFalse(containsLaneWidthIssue(validate(message)));
    }

    @Test
    void validate_pathThatDoublesBackReportsGeometryIssue() {
        TravelerInformationMessageFrame message = xyMessage(1L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(-10_000L, 0L));

        assertTrue(containsLaneWidthIssue(validate(message)));
    }

    @Test
    void validate_nonRightAngleBendUsesDirectionChangeInWidthLimit() {
        TravelerInformationMessageFrame withinLimit = xyMessage(5_000L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(-8_660L, 5_000L));
        TravelerInformationMessageFrame beyondLimit = xyMessage(6_000L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(-8_660L, 5_000L));

        assertFalse(containsLaneWidthIssue(validate(withinLimit)));
        assertTrue(containsLaneWidthIssue(validate(beyondLimit)));
    }

    @Test
    void validate_closedPathDoesNotApplyLaneWidthBendCheck() {
        TravelerInformationMessageFrame message = closedXyMessage(
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(0L, 10_000L),
                xyNode(-10_000L, 0L),
                xyNode(0L, -10_000L));
        // Best-practice validation still runs after schema validation fails, so explicitly
        // verify that a prohibited laneWidth does not trigger an additional geometry issue.
        firstRegion(message).setLaneWidth(new LaneWidth(32_767L));

        List<String> issues = validate(message);

        assertFalse(containsLaneWidthIssue(issues));
        assertFalse(containsLaneCorridorIssue(issues));
        assertFalse(containsClosedPolygonIssue(issues));
    }

    @Test
    void validate_openPathWithRepeatedPointReportsIssue() {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(1_000L, 0L),
                xyNode(1_000L, 0L),
                xyNode(-1_000L, 0L));

        List<String> issues = validate(message);

        assertTrue(issues.stream().anyMatch(issue -> issue.contains("open path must not contain repeated points")
                && issue.contains("indexes 0 and 2")));
        assertEquals(XY_NODES_PATH + "/2",
                findIssue(validateStructured(message), "open path must not contain repeated points").path());
    }

    @Test
    void validate_openPathThatIntersectsItselfReportsIssue() {
        TravelerInformationMessageFrame message = xyMessage(1L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 10_000L),
                xyNode(-10_000L, 0L),
                xyNode(10_000L, -10_000L));

        List<String> issues = validate(message);

        assertTrue(issues.stream().anyMatch(issue -> issue.contains("open path must not intersect itself")
                && issue.contains("6000.00 cm, 5000.00 cm")));
        assertFalse(containsLaneCorridorIssue(issues),
                "Corridor validation should not cascade after centerline topology fails");
        assertEquals(XY_NODES_PATH,
                findIssue(validateStructured(message), "open path must not intersect itself").path());
    }

    @Test
    void validate_simpleClosedPolygonDoesNotReportTopologyIssue() {
        TravelerInformationMessageFrame message = closedXyMessage(
                xyNode(1_000L, 0L),
                xyNode(1_000L, 0L),
                xyNode(0L, 1_000L),
                xyNode(-1_000L, 0L),
                xyNode(0L, -1_000L));

        assertNull(firstRegion(message).getLaneWidth());
        assertFalse(containsClosedPolygonIssue(validate(message)));
    }

    @Test
    void validate_closedPolygonThatIntersectsItselfReportsIssue() {
        TravelerInformationMessageFrame message = closedXyMessage(
                xyNode(1_000L, 0L),
                xyNode(1_000L, 0L),
                xyNode(-1_000L, 1_000L),
                xyNode(1_000L, 0L),
                xyNode(-1_000L, -1_000L));

        List<String> issues = validate(message);

        assertTrue(issues.stream().anyMatch(issue -> issue.contains("closed polygon must not intersect itself")
                && issue.contains("1500.00 cm, 500.00 cm")));
    }

    @Test
    void validate_closedPolygonWhoseEndpointsDoNotCoincideReportsIssue() {
        TravelerInformationMessageFrame message = closedXyMessage(
                xyNode(1_000L, 0L),
                xyNode(1_000L, 0L),
                xyNode(0L, 1_000L),
                xyNode(-1_000L, 0L),
                xyNode(100L, -1_000L));

        List<String> issues = validate(message);

        assertTrue(issues.stream().anyMatch(issue -> issue.contains(
                "closed polygon's first and last points must coincide")));
        assertEquals(XY_NODES_PATH + "/4",
                findIssue(validateStructured(message), "first and last points must coincide").path());
    }

    @Test
    void validate_closedPolygonWithRepeatedInternalPointReportsIssue() {
        TravelerInformationMessageFrame message = closedXyMessage(
                xyNode(1_000L, 0L),
                xyNode(1_000L, 0L),
                xyNode(0L, 1_000L),
                xyNode(0L, -1_000L),
                xyNode(-1_000L, 0L));

        List<String> issues = validate(message);

        assertTrue(issues.stream().anyMatch(issue -> issue.contains(
                "closed polygon must not contain repeated points")
                && issue.contains("indexes 1 and 3")));
    }

    @Test
    void validate_zoomScalesAnchorDistanceAndSegmentLengths() {
        TravelerInformationMessageFrame message = xyMessage(15_000L, 1,
                xyNode(500L, 0L),
                xyNode(5_000L, 0L),
                xyNode(0L, 5_000L));

        List<String> issues = validate(message);

        assertFalse(containsAnchorIssue(issues), "Zoom 1 must double the encoded anchor offset");
        assertFalse(containsLaneWidthIssue(issues), "Zoom 1 must double every encoded segment offset");
    }

    @Test
    void validate_diagonalAnchorOffsetUsesEuclideanDistance() {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(600L, 800L));

        assertFalse(containsAnchorIssue(validate(message)));
    }

    @Test
    void validate_anchorExactlyOneMeterFromRequiredDistanceIsAccepted() {
        TravelerInformationMessageFrame nineMeters = xyMessage(0L, 0,
                xyNode(900L, 0L));
        TravelerInformationMessageFrame elevenMeters = xyMessage(0L, 0,
                xyNode(1_100L, 0L));

        assertFalse(containsAnchorIssue(validate(nineMeters)));
        assertFalse(containsAnchorIssue(validate(elevenMeters)));
    }

    @Test
    void validate_anchorMoreThanOneMeterFromRequiredDistanceIsRejected() {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(899L, 0L));

        assertTrue(containsAnchorIssue(validate(message)));
    }

    @Test
    void validate_anchorNotTenMetersFromFirstNodeReportsAnchorIssue() {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(0L, 0L));

        assertTrue(containsAnchorIssue(validate(message)));
        assertTrue(validateStructured(message).stream()
                .anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR
                        && issue.message().contains("actual distance is 0.00 m")));
        assertEquals(REGION_PATH + "/anchor",
                findIssue(validateStructured(message), "actual distance is 0.00 m").path());
    }

    @Test
    void validate_anchorOnApproachTrajectoryDoesNotReportDirectionWarning() {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(1_000L, 0L),
                xyNode(1_000L, 0L));

        assertFalse(containsAnchorDirectionIssue(validate(message)));
    }

    @Test
    void validate_anchorBehindButNotOnApproachLineDoesNotReportDirectionWarning() {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(1_000L, 0L),
                xyNode(1L, 1_000L));

        assertFalse(containsAnchorDirectionIssue(validate(message)));
    }

    @Test
    void validate_anchorAheadOfFirstSegmentReportsWarning() {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(1_000L, 0L),
                xyNode(-500L, 0L));

        List<ValidationIssue> issues = validateStructured(message);

        assertTrue(issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.WARNING
                && issue.message().contains("anchor must be before the first path node")));
        assertEquals(REGION_PATH + "/anchor",
                findIssue(issues, "anchor must be before the first path node").path());
    }

    @Test
    void validate_xyAbsoluteLatLonNodeIsMeasuredFromAnchor() {
        Position3D equatorAnchor = anchor(0L, 0L);
        TravelerInformationMessageFrame message = message(
                region(equatorAnchor, 0L, pathDescription(0, absoluteXyNode(0L, 898L))));

        assertFalse(containsAnchorIssue(validate(message)),
                "An absolute longitude about 10 meters east of the anchor should pass");
    }

    @Test
    void validate_xyAbsoluteLatLonNodeResetsPositionForFollowingOffsets() {
        Position3D equatorAnchor = anchor(0L, 0L);
        TravelerInformationMessageFrame message = message(region(
                equatorAnchor,
                15_000L,
                pathDescription(0,
                        absoluteXyNode(0L, 898L),
                        xyNode(10_000L, 0L),
                        xyNode(0L, 10_000L))));

        List<String> issues = validate(message);

        assertFalse(containsAnchorIssue(issues));
        assertFalse(containsLaneWidthIssue(issues));
    }

    @Test
    void validate_latLonOffsetsUseWgs84DistanceAndZoom() {
        Position3D equatorAnchor = anchor(0L, 0L);
        TravelerInformationMessageFrame message = message(
                region(equatorAnchor, 0L, latLonPathDescription(1, llNode(0L, 452L))));

        assertFalse(containsAnchorIssue(validate(message)),
                "A zoomed latitude offset representing about 10 meters should pass");
    }

    private static List<String> validate(TravelerInformationMessageFrame message) {
        return new BestPracticesValidator().validate(message).stream()
                .map(ValidationIssue::message)
                .toList();
    }

    private static List<ValidationIssue> validateStructured(TravelerInformationMessageFrame message) {
        return new BestPracticesValidator().validate(message);
    }

    private static ValidationIssue findIssue(List<ValidationIssue> issues, String messagePart) {
        return issues.stream()
                .filter(issue -> issue.message().contains(messagePart))
                .findFirst()
                .orElseThrow();
    }

    private static TravelerInformationMessageFrame xyMessage(long laneWidthCm, int zoom, NodeXY... nodes) {
        return message(region(anchor(337_545_852L, -843_986_600L), laneWidthCm, pathDescription(zoom, nodes)));
    }

    private static TravelerInformationMessageFrame closedXyMessage(NodeXY... nodes) {
        GeographicalPath region = region(
                anchor(337_545_852L, -843_986_600L),
                pathDescription(0, nodes));
        region.setClosedPath(new Asn1Boolean(true));
        region.setDirection(new HeadingSlice());
        TravelerInformationMessageFrame message = message(region);
        return message;
    }

    private static TravelerInformationMessageFrame message(GeographicalPath region) {
        TravelerInformationMessageFrame messageFrame = new TravelerInformationMessageFrame();
        TravelerInformation tim = new TravelerInformation();
        TravelerDataFrameList dataFrames = new TravelerDataFrameList();
        TravelerDataFrame frame = new TravelerDataFrame();
        TravelerDataFrame.SequenceOfRegions regions = new TravelerDataFrame.SequenceOfRegions();

        regions.add(region);
        frame.setRegions(regions);
        dataFrames.add(frame);
        tim.setDataFrames(dataFrames);
        messageFrame.setValue(tim);
        return messageFrame;
    }

    private static GeographicalPath region(
            Position3D anchor,
            long laneWidthCm,
            GeographicalPath.DescriptionChoice description) {
        GeographicalPath region = region(anchor, description);
        region.setLaneWidth(new LaneWidth(laneWidthCm));
        return region;
    }

    private static GeographicalPath region(
            Position3D anchor,
            GeographicalPath.DescriptionChoice description) {
        GeographicalPath region = new GeographicalPath();
        region.setAnchor(anchor);
        region.setDescription(description);
        return region;
    }

    private static GeographicalPath firstRegion(TravelerInformationMessageFrame message) {
        return message.getValue().getDataFrames().getFirst().getRegions().getFirst();
    }

    private static Position3D anchor(long latitude, long longitude) {
        Position3D anchor = new Position3D();
        anchor.setLat(new Latitude(latitude));
        anchor.setLong_(new Longitude(longitude));
        anchor.setElevation(new Elevation(-4096L));
        return anchor;
    }

    private static GeographicalPath.DescriptionChoice pathDescription(int zoom, NodeXY... nodes) {
        NodeSetXY nodeSet = new NodeSetXY();
        for (NodeXY node : nodes) {
            nodeSet.add(node);
        }

        NodeListXY nodeList = new NodeListXY();
        nodeList.setNodes(nodeSet);

        OffsetSystem.OffsetChoice offsetChoice = new OffsetSystem.OffsetChoice();
        offsetChoice.setXy(nodeList);
        return pathDescription(zoom, offsetChoice);
    }

    private static GeographicalPath.DescriptionChoice latLonPathDescription(int zoom, NodeLL... nodes) {
        NodeSetLL nodeSet = new NodeSetLL();
        for (NodeLL node : nodes) {
            nodeSet.add(node);
        }

        NodeListLL nodeList = new NodeListLL();
        nodeList.setNodes(nodeSet);

        OffsetSystem.OffsetChoice offsetChoice = new OffsetSystem.OffsetChoice();
        offsetChoice.setLl(nodeList);
        return pathDescription(zoom, offsetChoice);
    }

    private static GeographicalPath.DescriptionChoice pathDescription(
            int zoom,
            OffsetSystem.OffsetChoice offsetChoice) {
        OffsetSystem offsetSystem = new OffsetSystem();
        offsetSystem.setScale(new Zoom(zoom));
        offsetSystem.setOffset(offsetChoice);

        GeographicalPath.DescriptionChoice description = new GeographicalPath.DescriptionChoice();
        description.setPath(offsetSystem);
        return description;
    }

    private static NodeXY xyNode(long xCm, long yCm) {
        Node_XY_32b value = new Node_XY_32b();
        value.setX(new Offset_B16(xCm));
        value.setY(new Offset_B16(yCm));

        NodeOffsetPointXY delta = new NodeOffsetPointXY();
        delta.setNode_XY6(value);
        return xyNode(delta);
    }

    private static NodeXY absoluteXyNode(long latitude, long longitude) {
        Node_LLmD_64b value = new Node_LLmD_64b();
        value.setLat(new Latitude(latitude));
        value.setLon(new Longitude(longitude));

        NodeOffsetPointXY delta = new NodeOffsetPointXY();
        delta.setNode_LatLon(value);
        return xyNode(delta);
    }

    private static NodeXY xyNode(NodeOffsetPointXY delta) {
        NodeXY node = new NodeXY();
        node.setDelta(delta);
        return node;
    }

    private static NodeLL llNode(long longitudeOffset, long latitudeOffset) {
        Node_LL_48B value = new Node_LL_48B();
        value.setLon(new OffsetLL_B24(longitudeOffset));
        value.setLat(new OffsetLL_B24(latitudeOffset));

        NodeOffsetPointLL delta = new NodeOffsetPointLL();
        delta.setNode_LL6(value);

        NodeLL node = new NodeLL();
        node.setDelta(delta);
        return node;
    }

    private static boolean containsLaneWidthIssue(List<String> issues) {
        return issues.stream().anyMatch(issue -> issue.contains("laneWidth") && issue.contains("exceeds"));
    }

    private static boolean containsLaneCorridorIssue(List<String> issues) {
        return issues.stream().anyMatch(issue -> issue.contains("lane corridor"));
    }

    private static boolean containsAnchorIssue(List<String> issues) {
        return issues.stream().anyMatch(issue -> issue.contains("anchor must be 10.00 m before the first path node"));
    }

    private static boolean containsAnchorDirectionIssue(List<String> issues) {
        return issues.stream().anyMatch(issue -> issue.contains("anchor must be before the first path node"));
    }

    private static boolean containsClosedPolygonIssue(List<String> issues) {
        return issues.stream().anyMatch(issue -> issue.contains("closed polygon"));
    }
}
