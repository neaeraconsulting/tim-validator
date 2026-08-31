package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import us.dot.its.jpo.asn.j2735.r2024.Common.Elevation;
import us.dot.its.jpo.asn.j2735.r2024.Common.LaneWidth;
import us.dot.its.jpo.asn.j2735.r2024.Common.Latitude;
import us.dot.its.jpo.asn.j2735.r2024.Common.Longitude;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeListXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeOffsetPointXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeSetXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.Node_XY_32b;
import us.dot.its.jpo.asn.j2735.r2024.Common.Offset_B16;
import us.dot.its.jpo.asn.j2735.r2024.Common.Position3D;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.OffsetSystem;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrameList;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;

class LaneCrossingGeometryValidatorTest {

    private static final long BASE_LATITUDE = 400_000_000L;
    private static final long BASE_LONGITUDE = -1_050_000_000L;
    private static final String SECOND_LANE_NODES_PATH =
            "/value/TravelerInformation/dataFrames/0/regions/1/description/path/offset/xy/nodes";

    @Test
    void validate_crossingCenterlinesReportOneError() {
        TravelerDataFrameList dataFrames = oneDataFrame(
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(1_000, 0),
                        xyNode(2_000, 0)),
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(2_000, -1_000),
                        xyNode(0, 2_000)));

        List<ValidationIssue> issues = LaneCrossingGeometryValidator.validate(dataFrames);

        assertEquals(1, issues.size(), "A crossing pair must not also report corridor overlap");
        ValidationIssue issue = issues.getFirst();
        assertEquals(ValidationSeverity.ERROR, issue.severity());
        assertTrue(issue.message().contains("lane centerlines must not cross"));
        assertEquals(SECOND_LANE_NODES_PATH, issue.path());
    }

    @Test
    void validate_parallelCorridorsWithInteriorOverlapReportWarning() {
        TravelerDataFrameList dataFrames = oneDataFrame(
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(1_000, 0),
                        xyNode(2_000, 0)),
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(1_000, 150),
                        xyNode(2_000, 0)));

        List<ValidationIssue> issues = LaneCrossingGeometryValidator.validate(dataFrames);

        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.WARNING, issues.getFirst().severity());
        assertTrue(issues.getFirst().message().contains("lane corridors overlap"));
    }

    @Test
    void validate_sharedCenterlineSegmentReportsWarning() {
        TravelerDataFrameList dataFrames = oneDataFrame(
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(1_000, 0),
                        xyNode(2_000, 0)),
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(2_000, 0),
                        xyNode(2_000, 0)));

        List<ValidationIssue> issues = LaneCrossingGeometryValidator.validate(dataFrames);

        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.WARNING, issues.getFirst().severity());
        assertTrue(issues.getFirst().message().contains("lane centerlines overlap"));
    }

    @Test
    void validate_crossingTakesPrecedenceWhenPairAlsoSharesSegment() {
        TravelerDataFrameList dataFrames = oneDataFrame(
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(1_000, 0),
                        xyNode(4_000, 0)),
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(2_000, 0),
                        xyNode(1_000, 0),
                        xyNode(1_000, -1_000),
                        xyNode(0, 2_000)));

        List<ValidationIssue> issues = LaneCrossingGeometryValidator.validate(dataFrames);

        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.ERROR, issues.getFirst().severity());
        assertTrue(issues.getFirst().message().contains("lane centerlines must not cross"));
    }

    @Test
    void validate_corridorsThatOnlyShareBoundaryDoNotReportWarning() {
        TravelerDataFrameList dataFrames = oneDataFrame(
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(1_000, 0),
                        xyNode(2_000, 0)),
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(1_000, 200),
                        xyNode(2_000, 0)));

        assertTrue(LaneCrossingGeometryValidator.validate(dataFrames).isEmpty());
    }

    @Test
    void validate_endpointOnlyContactDoesNotReportCorridorOverlap() {
        TravelerDataFrameList dataFrames = oneDataFrame(
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(1_000, 0),
                        xyNode(2_000, 0)),
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(3_000, 0),
                        xyNode(0, 2_000)));

        assertTrue(LaneCrossingGeometryValidator.validate(dataFrames).isEmpty());
    }

    @Test
    void validate_crossingLanesInDifferentDataFramesReportError() {
        TravelerDataFrameList dataFrames = separateDataFrames(
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(1_000, 0),
                        xyNode(2_000, 0)),
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(2_000, -1_000),
                        xyNode(0, 2_000)));

        List<ValidationIssue> issues = LaneCrossingGeometryValidator.validate(dataFrames);

        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.ERROR, issues.getFirst().severity());
        assertTrue(issues.getFirst().message().contains(
                "Data frame 0 region 0 and data frame 1 region 0"));
        assertEquals(
                "/value/TravelerInformation/dataFrames/1/regions/0/description/path/offset/xy/nodes",
                issues.getFirst().path());
    }

    @Test
    void validate_differentAnchorsAreReprojectedBeforeComparison() {
        // The second anchor is approximately 20 m east and 20 m south of the first.
        // Its northbound path therefore crosses the first anchor's eastbound path.
        TravelerDataFrameList dataFrames = oneDataFrame(
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(1_000, 0),
                        xyNode(2_000, 0)),
                lane(anchor(BASE_LATITUDE - 1_800, BASE_LONGITUDE + 2_340), 200,
                        xyNode(0, 1_000),
                        xyNode(0, 2_000)));

        List<ValidationIssue> issues = LaneCrossingGeometryValidator.validate(dataFrames);

        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.ERROR, issues.getFirst().severity());
        assertTrue(issues.getFirst().message().contains("lane centerlines must not cross"));
    }

    @Test
    void validate_bestPracticesFlowIncludesPairwiseLaneIssues() throws ValidationException {
        TravelerDataFrameList dataFrames = oneDataFrame(
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(1_000, 0),
                        xyNode(2_000, 0)),
                lane(anchor(BASE_LATITUDE, BASE_LONGITUDE), 200,
                        xyNode(2_000, -1_000),
                        xyNode(0, 2_000)));
        TravelerInformationMessageFrame message = message(dataFrames);

        List<ValidationIssue> issues = new BestPracticesValidator().validate(message);

        assertTrue(issues.stream().anyMatch(issue ->
                issue.severity() == ValidationSeverity.ERROR
                        && issue.message().contains("lane centerlines must not cross")));
    }

    private static TravelerDataFrameList oneDataFrame(GeographicalPath... lanes) {
        TravelerDataFrameList dataFrames = new TravelerDataFrameList();
        dataFrames.add(dataFrame(lanes));
        return dataFrames;
    }

    private static TravelerDataFrameList separateDataFrames(GeographicalPath... lanes) {
        TravelerDataFrameList dataFrames = new TravelerDataFrameList();
        for (GeographicalPath lane : lanes) {
            dataFrames.add(dataFrame(lane));
        }
        return dataFrames;
    }

    private static TravelerDataFrame dataFrame(GeographicalPath... lanes) {
        TravelerDataFrame.SequenceOfRegions regions = new TravelerDataFrame.SequenceOfRegions();
        for (GeographicalPath lane : lanes) {
            regions.add(lane);
        }

        TravelerDataFrame dataFrame = new TravelerDataFrame();
        dataFrame.setRegions(regions);
        return dataFrame;
    }

    private static TravelerInformationMessageFrame message(TravelerDataFrameList dataFrames) {
        TravelerInformation tim = new TravelerInformation();
        tim.setDataFrames(dataFrames);
        TravelerInformationMessageFrame message = new TravelerInformationMessageFrame();
        message.setValue(tim);
        return message;
    }

    private static GeographicalPath lane(
            Position3D anchor,
            long widthCentimeters,
            NodeXY... nodes) {
        NodeSetXY nodeSet = new NodeSetXY();
        for (NodeXY node : nodes) {
            nodeSet.add(node);
        }

        NodeListXY nodeList = new NodeListXY();
        nodeList.setNodes(nodeSet);
        OffsetSystem.OffsetChoice offsetChoice = new OffsetSystem.OffsetChoice();
        offsetChoice.setXy(nodeList);
        OffsetSystem path = new OffsetSystem();
        path.setOffset(offsetChoice);
        GeographicalPath.DescriptionChoice description = new GeographicalPath.DescriptionChoice();
        description.setPath(path);

        GeographicalPath lane = new GeographicalPath();
        lane.setAnchor(anchor);
        lane.setLaneWidth(new LaneWidth(widthCentimeters));
        lane.setDescription(description);
        return lane;
    }

    private static NodeXY xyNode(long xCentimeters, long yCentimeters) {
        Node_XY_32b offset = new Node_XY_32b();
        offset.setX(new Offset_B16(xCentimeters));
        offset.setY(new Offset_B16(yCentimeters));
        NodeOffsetPointXY delta = new NodeOffsetPointXY();
        delta.setNode_XY6(offset);
        NodeXY node = new NodeXY();
        node.setDelta(delta);
        return node;
    }

    private static Position3D anchor(long latitude, long longitude) {
        Position3D anchor = new Position3D();
        anchor.setLat(new Latitude(latitude));
        anchor.setLong_(new Longitude(longitude));
        anchor.setElevation(new Elevation(-4_096));
        return anchor;
    }
}
