package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import us.dot.its.jpo.asn.j2735.r2024.Common.ComputedLane;
import us.dot.its.jpo.asn.j2735.r2024.Common.Elevation;
import us.dot.its.jpo.asn.j2735.r2024.Common.HeadingSlice;
import us.dot.its.jpo.asn.j2735.r2024.Common.LaneID;
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
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.DistanceUnits;
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
import us.dot.its.jpo.timvalidator.config.ValidationOptions;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.road.RoadSegment;

class GeometryValidatorTest {

    private static final String REGION_PATH =
            "/value/TravelerInformation/dataFrames/0/regions/0";
    private static final String XY_NODES_PATH =
            REGION_PATH + "/description/path/offset/xy/nodes";

    @Test
    void validate_circleDoesNotReportMissingOffsetPathWarning() throws ValidationException {
        GeometricProjection geometry = new GeometricProjection();
        geometry.setCircle(new Circle());
        GeographicalPath.DescriptionChoice description =
                new GeographicalPath.DescriptionChoice();
        description.setGeometry(geometry);
        TravelerInformationMessageFrame message = message(region(
                anchor(337_545_852L, -843_986_600L),
                description));

        List<ValidationIssue> issues = validate(message);

        assertFalse(issues.stream().anyMatch(issue ->
                issue.message().contains("missing offset path description")));
    }

    @Test
    void validate_imperialCircleUnitsReportMetricRecommendation() throws ValidationException {
        TravelerInformationMessageFrame message = circleMessage(DistanceUnits.FOOT);

        ValidationIssue issue = findIssue(validate(message), "metric units are recommended");

        assertEquals(ValidationSeverity.WARNING, issue.severity());
        assertEquals(REGION_PATH + "/description/geometry/circle/units", issue.path());
    }

    @Test
    void validate_metricCircleUnitsDoNotReportMetricRecommendation() throws ValidationException {
        TravelerInformationMessageFrame message = circleMessage(DistanceUnits.METER);

        assertTrue(validate(message).stream().noneMatch(issue ->
                issue.message().contains("metric units are recommended")));
    }

    @Test
    void validate_laneWidthAtSuspiciousThresholdReportsUnitWarning() throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(
                20L,
                0,
                xyNode(1_000L, 0L),
                xyNode(1_000L, 0L));

        ValidationIssue issue = findIssue(validate(message), "suspiciously small");

        assertEquals(ValidationSeverity.WARNING, issue.severity());
        assertEquals(REGION_PATH + "/laneWidth", issue.path());
        assertTrue(issue.message().contains("expressed in centimeters"));
    }

    @Test
    void validate_laneWidthAboveSuspiciousThresholdDoesNotReportUnitWarning()
        throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(
                21L,
                0,
                xyNode(1_000L, 0L),
                xyNode(1_000L, 0L));

        assertTrue(validate(message).stream().noneMatch(issue ->
                issue.message().contains("suspiciously small")));
    }

    @Test
    void validate_xyPathAtMaximumRecommendedSeparationDoesNotReportEncodingWarning()
        throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(
                300L,
                0,
                xyNode(1_000L, 0L),
                xyNode(32_767L, 0L));

        assertTrue(validate(message).stream().noneMatch(issue ->
                issue.message().contains("nodes are recommended because")));
    }

    @Test
    void validate_xyPathBeyondMaximumRecommendedSeparationRecommendsLlOffsets()
        throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(
                300L,
                0,
                xyNode(1_000L, 0L),
                xyNode(32_767L, 0L),
                xyNode(1L, 0L));

        ValidationIssue issue = findIssue(validate(message),
                "latitude/longitude offset nodes are recommended");

        assertEquals(ValidationSeverity.WARNING, issue.severity());
        assertEquals(REGION_PATH + "/description/path/offset", issue.path());
        assertTrue(issue.message().contains("327.68 m"));
    }

    @Test
    void validate_shortLlPathRecommendsXyOffsets() throws ValidationException {
        TravelerInformationMessageFrame message = message(region(
                anchor(0L, 0L),
                300L,
                latLonPathDescription(
                        0,
                        llNode(898L, 0L),
                        llNode(898L, 0L))));

        ValidationIssue issue = findIssue(validate(message), "XY offset nodes are recommended");

        assertEquals(ValidationSeverity.WARNING, issue.severity());
        assertEquals(REGION_PATH + "/description/path/offset", issue.path());
    }

    @Test
    void validate_longLlPathDoesNotReportEncodingWarning() throws ValidationException {
        TravelerInformationMessageFrame message = message(region(
                anchor(0L, 0L),
                300L,
                latLonPathDescription(
                        0,
                        llNode(898L, 0L),
                        llNode(40_000L, 0L))));

        assertTrue(validate(message).stream().noneMatch(issue ->
                issue.message().contains("nodes are recommended because")));
    }

    @Test
    void validate_llPathAtMaximumComponentSeparationDoesNotReportEncodingWarning()
        throws ValidationException {
        TravelerInformationMessageFrame message = message(region(
                anchor(0L, 0L),
                300L,
                latLonPathDescription(
                        0,
                        llNode(898L, 0L),
                        llNode(8_388_607L, 0L))));

        assertTrue(validate(message).stream().noneMatch(issue ->
                issue.message().contains("nodes are recommended because")));
    }

    @Test
    void validate_llPathBeyondMaximumComponentSeparationRecommendsAbsoluteNodes()
        throws ValidationException {
        TravelerInformationMessageFrame message = message(region(
                anchor(0L, 0L),
                300L,
                latLonPathDescription(
                        0,
                        llNode(898L, 0L),
                        llNode(8_388_607L, 0L),
                        llNode(1L, 0L))));

        ValidationIssue issue = findIssue(validate(message),
                "absolute latitude/longitude nodes are recommended");

        assertEquals(ValidationSeverity.WARNING, issue.severity());
        assertEquals(REGION_PATH + "/description/path/offset", issue.path());
        assertTrue(issue.message().contains("greater than 0.8388607 degrees"));
    }

    @Test
    void validate_emptyPathReportsGeometryNotEvaluatedWarning() throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(0L, 0);

        List<ValidationIssue> issues = validate(message);

        assertTrue(issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.WARNING
                && issue.message().contains("geometry not evaluated due to an empty path")));
        assertEquals(XY_NODES_PATH, findIssue(issues, "empty path").path());
    }

    @Test
    void validate_undecodablePathReportsReasonAsWarning() throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(new NodeOffsetPointXY()));

        List<ValidationIssue> issues = validate(message);

        assertTrue(issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.WARNING
                && issue.message().contains("geometry not evaluated due to")
                && issue.message().contains("unsupported offset choice")));
        assertEquals(XY_NODES_PATH + "/0/delta",
                findIssue(issues, "unsupported offset choice").path());
    }

    @Test
    void validate_rightAngleLaneWidthWithinLimitDoesNotReportGeometryIssue()
        throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(15_000L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(0L, 10_000L));

        List<ValidationIssue> issues = validate(message);

        assertFalse(containsLaneWidthIssue(issues),
                "A 150 meter centered width should fit a right-angle bend with 100 meter segments");
        assertFalse(containsLaneCorridorIssue(issues));
    }

    @Test
    void validate_laneWidthExactlyAtBendLimitDoesNotReportBendWidthIssue()
        throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(20_000L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(0L, 10_000L));

        assertFalse(containsLaneWidthIssue(validate(message)));
    }

    @Test
    void validate_laneWidthBeyondLimitReportsGeometryIssue() throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(25_000L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(0L, 10_000L));

        assertTrue(containsLaneWidthIssue(validate(message)));
        assertEquals(REGION_PATH + "/laneWidth",
                findIssue(validate(message), "exceeds maximum allowable").path());
    }

    @Test
    void validate_twoBendsSharingSegmentAreEvaluatedIndependently() throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(15_000L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(0L, 10_000L),
                xyNode(-10_000L, 0L));

        assertFalse(containsLaneWidthIssue(validate(message)),
                "Each three-point bend independently allows a 200 meter centered width");
    }

    @Test
    void validate_laneCorridorWithOverlappingNonAdjacentSectionsReportsIssue()
        throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(15_000L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(0L, 10_000L),
                xyNode(-10_000L, 0L));

        List<ValidationIssue> issues = validate(message);

        assertFalse(containsLaneWidthIssue(issues),
                "Each individual bend remains within the local bend-width limit");
        assertTrue(containsLaneCorridorIssue(issues),
                "The complete corridor must detect overlap between the two parallel sections");
        assertEquals(XY_NODES_PATH,
                findIssue(validate(message), "lane corridor").path());
    }

    @Test
    void validate_straightPathDoesNotImposeLaneWidthLimit() throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(32_767L, 0,
                xyNode(1_000L, 0L),
                xyNode(100L, 0L),
                xyNode(100L, 0L));

        assertFalse(containsLaneWidthIssue(validate(message)));
    }

    @Test
    void validate_pathThatDoublesBackReportsGeometryIssue() throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(1L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(-10_000L, 0L));

        assertTrue(containsLaneWidthIssue(validate(message)));
    }

    @Test
    void validate_nonRightAngleBendUsesDirectionChangeInWidthLimit() throws ValidationException {
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
    void validate_closedPathDoesNotApplyLaneWidthBendCheck() throws ValidationException {
        TravelerInformationMessageFrame message = closedXyMessage(
                xyNode(1_000L, 0L),
                xyNode(10_000L, 0L),
                xyNode(0L, 10_000L),
                xyNode(-10_000L, 0L),
                xyNode(0L, -10_000L));
        // Best-practice validation still runs after schema validation fails, so explicitly
        // verify that a prohibited laneWidth does not trigger an additional geometry issue.
        firstRegion(message).setLaneWidth(new LaneWidth(32_767L));

        List<ValidationIssue> issues = validate(message);

        assertFalse(containsLaneWidthIssue(issues));
        assertFalse(containsLaneCorridorIssue(issues));
        assertFalse(containsClosedPolygonIssue(issues));
    }

    @Test
    void validate_openPathWithRepeatedPointReportsIssue() throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(1_000L, 0L),
                xyNode(1_000L, 0L),
                xyNode(-1_000L, 0L));

        List<ValidationIssue> issues = validate(message);

        assertTrue(issues.stream().anyMatch(issue ->
                issue.message().contains("open path must not contain repeated points")
                        && issue.message().contains("indexes 0 and 2")));
        assertEquals(XY_NODES_PATH + "/2",
                findIssue(issues, "open path must not contain repeated points").path());
    }

    @Test
    void validate_openPathThatIntersectsItselfReportsIssue() throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(1L, 0,
                xyNode(1_000L, 0L),
                xyNode(10_000L, 10_000L),
                xyNode(-10_000L, 0L),
                xyNode(10_000L, -10_000L));

        List<ValidationIssue> issues = validate(message);

        assertTrue(issues.stream().anyMatch(issue ->
                issue.message().contains("open path must not intersect itself")
                        && issue.message().contains("6000.00 cm, 5000.00 cm")));
        assertFalse(containsLaneCorridorIssue(issues),
                "Corridor validation should not cascade after centerline topology fails");
        assertEquals(XY_NODES_PATH,
                findIssue(validate(message), "open path must not intersect itself").path());
    }

    @Test
    void validate_simpleClosedPolygonDoesNotReportTopologyIssue() throws ValidationException {
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
    void validate_closedPolygonThatIntersectsItselfReportsIssue() throws ValidationException {
        TravelerInformationMessageFrame message = closedXyMessage(
                xyNode(1_000L, 0L),
                xyNode(1_000L, 0L),
                xyNode(-1_000L, 1_000L),
                xyNode(1_000L, 0L),
                xyNode(-1_000L, -1_000L));

        List<ValidationIssue> issues = validate(message);

        assertTrue(issues.stream().anyMatch(issue ->
                issue.message().contains("closed polygon must not intersect itself")
                        && issue.message().contains("1500.00 cm, 500.00 cm")));
    }

    @Test
    void validate_closedPolygonWhoseEndpointsDoNotCoincideReportsIssue()
        throws ValidationException {
        TravelerInformationMessageFrame message = closedXyMessage(
                xyNode(1_000L, 0L),
                xyNode(1_000L, 0L),
                xyNode(0L, 1_000L),
                xyNode(-1_000L, 0L),
                xyNode(100L, -1_000L));

        List<ValidationIssue> issues = validate(message);

        assertTrue(issues.stream().anyMatch(issue -> issue.message().contains(
                "closed polygon's first and last points must coincide")));
        assertEquals(XY_NODES_PATH + "/4",
                findIssue(validate(message), "first and last points must coincide").path());
    }

    @Test
    void validate_closedPolygonWithRepeatedInternalPointReportsIssue() throws ValidationException {
        TravelerInformationMessageFrame message = closedXyMessage(
                xyNode(1_000L, 0L),
                xyNode(1_000L, 0L),
                xyNode(0L, 1_000L),
                xyNode(0L, -1_000L),
                xyNode(-1_000L, 0L));

        List<ValidationIssue> issues = validate(message);

        assertTrue(issues.stream().anyMatch(issue -> issue.message().contains(
                "closed polygon must not contain repeated points")
                && issue.message().contains("indexes 1 and 3")));
    }

    @Test
    void validate_zoomScalesAnchorDistanceAndSegmentLengths() throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(15_000L, 1,
                xyNode(500L, 0L),
                xyNode(5_000L, 0L),
                xyNode(0L, 5_000L));

        List<ValidationIssue> issues = validate(message);

        assertFalse(containsAnchorIssue(issues), "Zoom 1 must double the encoded anchor offset");
        assertFalse(containsLaneWidthIssue(issues), "Zoom 1 must double every encoded segment offset");
    }

    @Test
    void validate_diagonalAnchorOffsetUsesEuclideanDistance() throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(600L, 800L));

        assertFalse(containsAnchorIssue(validate(message)));
    }

    @Test
    void validate_anchorExactlyOneMeterFromRequiredDistanceIsAccepted() throws ValidationException {
        TravelerInformationMessageFrame nineMeters = xyMessage(0L, 0,
                xyNode(900L, 0L));
        TravelerInformationMessageFrame elevenMeters = xyMessage(0L, 0,
                xyNode(1_100L, 0L));

        assertFalse(containsAnchorIssue(validate(nineMeters)));
        assertFalse(containsAnchorIssue(validate(elevenMeters)));
    }

    @Test
    void validate_anchorMoreThanOneMeterFromRequiredDistanceIsRejected()
        throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(899L, 0L));

        assertTrue(containsAnchorIssue(validate(message)));
    }

    @Test
    void validate_anchorNotTenMetersFromFirstNodeReportsAnchorIssue() throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(0L, 0L));

        assertTrue(containsAnchorIssue(validate(message)));
        assertTrue(validate(message).stream()
                .anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR
                        && issue.message().contains("actual distance is 0.00 m")));
        assertEquals(REGION_PATH + "/anchor",
                findIssue(validate(message), "actual distance is 0.00 m").path());
    }

    @Test
    void validate_anchorOnApproachTrajectoryDoesNotReportDirectionWarning()
        throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(1_000L, 0L),
                xyNode(1_000L, 0L));

        assertFalse(containsAnchorDirectionIssue(validate(message)));
    }

    @Test
    void validate_anchorBehindButNotOnApproachLineDoesNotReportDirectionWarning()
        throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(1_000L, 0L),
                xyNode(1L, 1_000L));

        assertFalse(containsAnchorDirectionIssue(validate(message)));
    }

    @Test
    void validate_anchorAheadOfFirstSegmentReportsWarning() throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(0L, 0,
                xyNode(1_000L, 0L),
                xyNode(-500L, 0L));

        List<ValidationIssue> issues = validate(message);

        assertTrue(issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.WARNING
                && issue.message().contains("anchor must be before the first path node")));
        assertEquals(REGION_PATH + "/anchor",
                findIssue(issues, "anchor must be before the first path node").path());
    }

    @Test
    void validate_xyAbsoluteLatLonNodeIsMeasuredFromAnchor() throws ValidationException {
        Position3D equatorAnchor = anchor(0L, 0L);
        TravelerInformationMessageFrame message = message(
                region(equatorAnchor, 0L, pathDescription(0, absoluteXyNode(0L, 898L))));

        assertFalse(containsAnchorIssue(validate(message)),
                "An absolute longitude about 10 meters east of the anchor should pass");
    }

    @Test
    void validate_xyAbsoluteLatLonNodeResetsPositionForFollowingOffsets()
        throws ValidationException {
        Position3D equatorAnchor = anchor(0L, 0L);
        TravelerInformationMessageFrame message = message(region(
                equatorAnchor,
                15_000L,
                pathDescription(0,
                        absoluteXyNode(0L, 898L),
                        xyNode(10_000L, 0L),
                        xyNode(0L, 10_000L))));

        List<ValidationIssue> issues = validate(message);

        assertFalse(containsAnchorIssue(issues));
        assertFalse(containsLaneWidthIssue(issues));
    }

    @Test
    void validate_latLonOffsetsUseWgs84DistanceAndZoom() throws ValidationException {
        Position3D equatorAnchor = anchor(0L, 0L);
        TravelerInformationMessageFrame message = message(
                region(equatorAnchor, 0L, latLonPathDescription(1, llNode(0L, 452L))));

        assertFalse(containsAnchorIssue(validate(message)),
                "A zoomed latitude offset representing about 10 meters should pass");
    }

    @Test
    void validate_computedLaneReturnsReferenceLaneWarning() throws ValidationException {
        TravelerInformationMessageFrame message = message(region(
                anchor(337_545_852L, -843_986_600L),
                300L,
                computedPathDescription(7L)));

        List<ValidationIssue> issues = validate(message);

        List<ValidationIssue> computedLaneWarnings = issues.stream()
                .filter(issue -> issue.path() != null
                        && issue.path().endsWith("/computed/referenceLaneId"))
                .toList();

        assertEquals(1, computedLaneWarnings.size());
        ValidationIssue issue = computedLaneWarnings.getFirst();
        assertEquals(ValidationSeverity.WARNING, issue.severity());
        assertEquals("Best Practices", issue.checkName());
        assertEquals(
                "/value/TravelerInformation/dataFrames/0/regions/0/description/path/offset/xy/"
                        + "computed/referenceLaneId",
                issue.path());
        assertTrue(issue.message().contains("uses a computed lane"));
        assertTrue(issue.message().contains(
                "referenceLaneId identify the left-most lane in the direction of traffic"));
        assertTrue(issues.stream().noneMatch(candidate ->
                candidate.message().contains("geometry not evaluated")));
    }

    @Test
    void validate_explicitNodeListDoesNotReturnComputedLaneWarning() throws ValidationException {
        TravelerInformationMessageFrame message = xyMessage(
                300L,
                0,
                xyNode(1_000L, 0L));

        assertTrue(validate(message).stream().noneMatch(issue ->
                issue.message().contains("uses a computed lane")));
    }

    @Test
    void validate_missingPathChoicesDoNotReturnComputedLaneWarning() throws ValidationException {
        GeographicalPath.DescriptionChoice emptyDescription =
                new GeographicalPath.DescriptionChoice();
        TravelerInformationMessageFrame message = message(region(
                anchor(337_545_852L, -843_986_600L),
                300L,
                emptyDescription));

        assertTrue(validate(message).stream().noneMatch(issue ->
                issue.message().contains("uses a computed lane")));
    }

    @Test
    void validate_headingMismatchReturnsStructuredGeometryWarning() throws ValidationException {
        BestPracticesValidator validator = new BestPracticesValidator(
                (location, radius) -> List.of(new RoadSegment(
                        202L,
                        "Broadway",
                        List.of(
                                new Coordinate(-105.001, 40.0),
                                new Coordinate(-104.999, 40.0)))));

        List<ValidationIssue> issues =
                validator.validate(messageWithHeading(0));

        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.WARNING, issues.getFirst().severity());
        assertEquals("Best Practices", issues.getFirst().checkName());
        assertEquals(
                "/value/TravelerInformation/dataFrames/0/regions/0/direction",
                issues.getFirst().path());
        assertTrue(issues.getFirst().message().contains("not tangent"));
    }

    @Test
    void validate_disabledHeadingCheckDoesNotCallRoadProvider() throws ValidationException {
        AtomicInteger providerCalls = new AtomicInteger();
        BestPracticesValidator validator = new BestPracticesValidator(
                (location, radius) -> {
                    providerCalls.incrementAndGet();
                    return List.of();
                });

        List<ValidationIssue> issues = validator.validate(
                messageWithHeading(0),
                ValidationOptions.networkFree());

        assertEquals(0, providerCalls.get());
        assertTrue(issues.stream().noneMatch(issue ->
                issue.message().contains("roadway heading")));
    }

    @Test
    void validate_missingOptionalTimDoesNotCallRoadProvider() throws ValidationException {
        AtomicInteger providerCalls = new AtomicInteger();
        BestPracticesValidator validator = new BestPracticesValidator(
                (location, radius) -> {
                    providerCalls.incrementAndGet();
                    return List.of();
                });

        List<ValidationIssue> issues =
                validator.validate(new TravelerInformationMessageFrame());

        assertEquals(0, providerCalls.get());
        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.ERROR, issues.getFirst().severity());
        assertEquals("Best Practices", issues.getFirst().checkName());
        assertNull(issues.getFirst().path());
        assertTrue(issues.getFirst().message()
                .contains("not a TravelerInformationMessageFrame"));
    }

    private static List<ValidationIssue> validate(TravelerInformationMessageFrame message)
        throws ValidationException {
        return new BestPracticesValidator().validate(message);
    }

    private static TravelerInformationMessageFrame messageWithHeading(int headingIndex) {
        HeadingSlice heading = new HeadingSlice();
        heading.set(headingIndex, true);

        TravelerInformationMessageFrame message = closedXyMessage(
                xyNode(1_000L, 0L),
                xyNode(4_000L, -5_000L),
                xyNode(0L, 10_000L),
                xyNode(-10_000L, 0L),
                xyNode(0L, -10_000L),
                xyNode(6_000L, 5_000L));
        GeographicalPath region = firstRegion(message);
        region.setAnchor(anchor(400_000_000L, -1_050_000_000L));
        region.setDirection(heading);
        return message;
    }

    private static TravelerInformationMessageFrame circleMessage(DistanceUnits units) {
        Circle circle = new Circle();
        circle.setUnits(units);
        GeometricProjection geometry = new GeometricProjection();
        geometry.setCircle(circle);
        GeographicalPath.DescriptionChoice description = new GeographicalPath.DescriptionChoice();
        description.setGeometry(geometry);
        return message(region(anchor(337_545_852L, -843_986_600L), description));
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

    /** Builds a path whose XY node-list choice contains a computed lane. */
    private static GeographicalPath.DescriptionChoice computedPathDescription(long referenceLaneId) {
        ComputedLane computedLane = new ComputedLane();
        computedLane.setReferenceLaneId(new LaneID(referenceLaneId));

        NodeListXY nodeList = new NodeListXY();
        nodeList.setComputed(computedLane);

        OffsetSystem.OffsetChoice offsetChoice = new OffsetSystem.OffsetChoice();
        offsetChoice.setXy(nodeList);
        return pathDescription(0, offsetChoice);
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

    private static boolean containsLaneWidthIssue(List<ValidationIssue> issues) {
        return issues.stream().anyMatch(issue ->
                issue.message().contains("laneWidth")
                        && issue.message().contains("exceeds"));
    }

    private static boolean containsLaneCorridorIssue(List<ValidationIssue> issues) {
        return issues.stream().anyMatch(issue ->
                issue.message().contains("lane corridor"));
    }

    private static boolean containsAnchorIssue(List<ValidationIssue> issues) {
        return issues.stream().anyMatch(issue ->
                issue.message().contains(
                        "anchor must be 10.00 m before the first path node"));
    }

    private static boolean containsClosedPolygonIssue(List<ValidationIssue> issues) {
        return issues.stream().anyMatch(issue ->
                issue.message().contains("closed polygon"));
    }

    private static boolean containsAnchorDirectionIssue(List<ValidationIssue> issues) {
        return issues.stream().anyMatch(issue ->
                issue.message().contains("anchor must be before the first path node"));
    }
}
