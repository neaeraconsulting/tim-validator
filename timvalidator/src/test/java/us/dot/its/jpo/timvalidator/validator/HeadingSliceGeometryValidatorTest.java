package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;

import us.dot.its.jpo.asn.j2735.r2024.Common.Elevation;
import us.dot.its.jpo.asn.j2735.r2024.Common.HeadingSlice;
import us.dot.its.jpo.asn.j2735.r2024.Common.Latitude;
import us.dot.its.jpo.asn.j2735.r2024.Common.Longitude;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeListXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeOffsetPointXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeSetXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.NodeXY;
import us.dot.its.jpo.asn.j2735.r2024.Common.Node_XY_32b;
import us.dot.its.jpo.asn.j2735.r2024.Common.Offset_B16;
import us.dot.its.jpo.asn.j2735.r2024.Common.Position3D;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Circle;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.DistanceUnits;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeometricProjection;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.OffsetSystem;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Radius_B12;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Zoom;
import us.dot.its.jpo.asn.runtime.types.Asn1Boolean;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.road.RoadGeometryProvider;
import us.dot.its.jpo.timvalidator.road.RoadSegment;

class HeadingSliceGeometryValidatorTest {

    private static final double LATITUDE = 40.0;
    private static final double LONGITUDE = -105.0;

    @Test
    void validate_centerSliceTangentToClosestRoadProducesNoIssue() throws ValidationException {
        GeographicalPath region = region(heading(0));
        HeadingSliceGeometryValidator validator = validator(northSouthRoad(101, "Main Street", 0.0));

        List<ValidationIssue> issues = validator.validate(region, 2, 3);

        assertTrue(issues.isEmpty());
    }

    @Test
    void validate_oppositeCentersAreBothTangentToTheSameRoadAxis() throws ValidationException {
        GeographicalPath region = region(heading(0, 8));
        HeadingSliceGeometryValidator validator = validator(northSouthRoad(101, "Main Street", 0.0));

        List<ValidationIssue> issues = validator.validate(region, 0, 0);

        assertTrue(issues.isEmpty());
    }

    @Test
    void validate_wrappedThreeSliceRangeUsesItsCenterSlice() throws ValidationException {
        GeographicalPath region = region(heading(15, 0, 1));
        HeadingSliceGeometryValidator validator = validator(northSouthRoad(101, "Main Street", 0.0));

        List<ValidationIssue> issues = validator.validate(region, 0, 0);

        assertTrue(issues.isEmpty());
    }

    @Test
    void validate_nonTangentCenterReturnsStructuredWarning() throws ValidationException {
        GeographicalPath region = region(heading(0));
        HeadingSliceGeometryValidator validator = validator(eastWestRoad(202, "Broadway", 0.0));

        List<ValidationIssue> issues = validator.validate(region, 2, 3);

        assertEquals(1, issues.size());
        ValidationIssue issue = issues.getFirst();
        assertEquals(ValidationSeverity.WARNING, issue.severity());
        assertEquals("Best Practices", issue.checkName());
        assertEquals("/value/TravelerInformation/dataFrames/2/regions/3/direction", issue.path());
        assertTrue(issue.message().contains("not tangent"));
        assertTrue(issue.message().contains("road segment 202 (Broadway)"));
    }

    @Test
    void validate_circleHeadingUsesCircleCenterAndNestedPath() throws ValidationException {
        GeographicalPath region = nestedGeometryRegion(heading(0));
        HeadingSliceGeometryValidator validator = new HeadingSliceGeometryValidator(
                (location, radius) -> {
                    assertEquals(LONGITUDE, location.getX());
                    assertEquals(LATITUDE, location.getY());
                    assertEquals(30.0, radius);
                    return List.of(eastWestRoad(202, null, 0.0));
                });

        List<ValidationIssue> issues = validator.validate(region, 0, 1);

        assertEquals(1, issues.size());
        assertEquals(
                "/value/TravelerInformation/dataFrames/0/regions/1/description/geometry/direction",
                issues.getFirst().path());
    }

    @Test
    void validate_missingNoHeadingAndAllHeadingsDoNotCallProvider() throws ValidationException {
        AtomicInteger calls = new AtomicInteger();
        RoadGeometryProvider provider = (location, radius) -> {
            calls.incrementAndGet();
            return List.of(northSouthRoad(101, null, 0.0));
        };
        HeadingSliceGeometryValidator validator =
                new HeadingSliceGeometryValidator(provider);

        assertTrue(validator.validate(region(null), 0, 0).isEmpty());
        assertTrue(validator.validate(region(new HeadingSlice()), 0, 0).isEmpty());

        HeadingSlice allHeadings = new HeadingSlice();
        for (int index = 0; index < allHeadings.size(); index++) {
            allHeadings.set(index, true);
        }
        assertTrue(validator.validate(region(allHeadings), 0, 0).isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void validate_evenWidthHeadingRangeUsesMidpointAndCallsProvider() throws ValidationException {
        AtomicInteger calls = new AtomicInteger();
        HeadingSliceGeometryValidator validator = new HeadingSliceGeometryValidator(
                (location, radius) -> {
                    calls.incrementAndGet();
                    assertTrue(radius > 0.0);
                    return List.of(northSouthRoad(101, "Main Street", 0.0));
                });

        List<ValidationIssue> issues = validator.validate(region(heading(0, 1)), 0, 0);

        assertTrue(issues.isEmpty(), issues.toString());
        assertEquals(1, calls.get());
    }

    @Test
    void validate_wrappedEvenWidthRangeUsesCircularMidpoint() throws ValidationException {
        HeadingSliceGeometryValidator validator =
                validator(northSouthRoad(101, "Main Street", 0.0));

        List<ValidationIssue> issues =
                validator.validate(region(heading(15, 0)), 0, 0);

        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void validate_multipleEvenWidthRangesAreCheckedIndependently() throws ValidationException {
        HeadingSliceGeometryValidator validator = validator(
                northSouthRoad(101, "Main Street", 0.0),
                eastWestRoad(202, "Broadway", 0.0));

        List<ValidationIssue> issues =
                validator.validate(region(heading(0, 1, 4, 5)), 0, 0);

        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void validate_oddWidthRangeAlsoUsesPlusOrMinusOneSliceTolerance() throws ValidationException {
        HeadingSliceGeometryValidator validator =
                validator(roadAtBearing(303, "Diagonal Road", 32.0));

        List<ValidationIssue> issues =
                validator.validate(region(heading(0)), 0, 0);

        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void validate_providerFailureReturnsNonBlockingWarning() throws ValidationException {
        HeadingSliceGeometryValidator validator = new HeadingSliceGeometryValidator(
                (location, radius) -> {
                    throw new IllegalStateException("service unavailable");
                });

        List<ValidationIssue> issues = validator.validate(region(heading(0)), 0, 0);

        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.WARNING, issues.getFirst().severity());
        assertTrue(issues.getFirst().message().contains("service unavailable"));
    }

    @Test
    void validate_undecodableRegionReturnsNonBlockingWarning() throws ValidationException {
        GeographicalPath region = new GeographicalPath();
        region.setDirection(heading(0));
        HeadingSliceGeometryValidator validator = validator();

        List<ValidationIssue> issues = validator.validate(region, 2, 3);

        assertEquals(1, issues.size());
        ValidationIssue issue = issues.getFirst();
        assertEquals(ValidationSeverity.WARNING, issue.severity());
        assertEquals("Best Practices", issue.checkName());
        assertEquals("/value/TravelerInformation/dataFrames/2/regions/3/direction", issue.path());
        assertTrue(issue.message().contains("could not be evaluated"));
        assertTrue(issue.message().contains("incomplete or malformed"));
    }

    @Test
    void validate_noNearbyRoadReturnsWarning() throws ValidationException {
        HeadingSliceGeometryValidator validator =
                new HeadingSliceGeometryValidator(
                        (location, radius) -> List.of());

        List<ValidationIssue> issues = validator.validate(region(heading(0)), 0, 0);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().message().contains("no mapped roadway"));
    }

    @Test
    void validate_headingMayMatchEitherRoadAtPerpendicularIntersection()
        throws ValidationException {
        HeadingSliceGeometryValidator validator = validator(
                northSouthRoad(101, "Main Street", 0.0),
                eastWestRoad(202, "Broadway", 0.0));

        List<ValidationIssue> issues = validator.validate(region(heading(0)), 0, 0);

        assertTrue(issues.isEmpty());
    }

    @Test
    void validate_eachHeadingRangeMayMatchADifferentIntersectionRoad() throws ValidationException {
        HeadingSliceGeometryValidator validator = validator(
                northSouthRoad(101, "Main Street", 0.0),
                eastWestRoad(202, "Broadway", 0.0));

        List<ValidationIssue> issues = validator.validate(region(heading(0, 4)), 0, 0);

        assertTrue(issues.isEmpty());
    }

    @Test
    void validate_roadOutsidePolygonDoesNotSatisfyHeading() throws ValidationException {
        HeadingSliceGeometryValidator validator = validator(
                eastWestRoad(202, "Closest Road", 0.0),
                northSouthRoad(101, "Road Outside Polygon", 60.0));

        List<ValidationIssue> issues = validator.validate(region(heading(0)), 0, 0);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().message().contains(
                "not tangent to any mapped roadway segment inside the TIM region"));
        assertTrue(issues.getFirst().message().contains("road segment 202 (Closest Road)"));
        assertFalse(issues.getFirst().message().contains("Road Outside Polygon"));
    }

    @Test
    void validate_anyRoadInsidePolygonMaySatisfyHeading() throws ValidationException {
        HeadingSliceGeometryValidator validator = validator(
                eastWestRoad(202, "Closest Road", 0.0),
                northSouthRoad(101, "Cross Street", 40.0));

        List<ValidationIssue> issues = validator.validate(region(heading(0)), 0, 0);

        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void validate_closedPathUsesPolygonInsteadOfAnchorProximity() throws ValidationException {
        GeographicalPath offsetPolygon = closedPathRegion(
                heading(0),
                xyNode(2_000L, -5_000L),
                xyNode(2_000L, 0L),
                xyNode(0L, 10_000L),
                xyNode(-2_000L, 0L),
                xyNode(0L, -10_000L));
        RoadGeometryProvider provider = new RoadGeometryProvider() {
            @Override
            public List<RoadSegment> findNearbyRoads(
                    Coordinate location,
                    double radiusMeters) {
                throw new AssertionError("Polygon-capable provider should receive the search polygon");
            }

            @Override
            public List<RoadSegment> findRoadsIn(Polygon searchArea) {
                assertTrue(searchArea.getEnvelopeInternal().getMinX() > LONGITUDE);
                return List.of(northSouthRoad(101, "Road Inside Polygon", 30.0));
            }
        };

        List<ValidationIssue> issues =
                new HeadingSliceGeometryValidator(provider).validate(offsetPolygon, 0, 0);

        assertTrue(issues.isEmpty());
    }

    @Test
    void validate_roadTouchingPolygonAtOnlyOnePointDoesNotQualify() throws ValidationException {
        RoadSegment touchingRoad = localRoad(
                101,
                "Touching Road",
                new Coordinate(0.0, -100.0),
                new Coordinate(0.0, -50.0));

        List<ValidationIssue> issues =
                validator(touchingRoad).validate(region(heading(0)), 0, 0);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().message().contains(
                "no mapped roadway segment inside the TIM region"));
    }

    @Test
    void validate_circleUsesRadiusAndExcludesRoadOutsideCircle() throws ValidationException {
        GeographicalPath circleRegion =
                circleRegion(heading(0), 20L, DistanceUnits.METER);
        HeadingSliceGeometryValidator validator = new HeadingSliceGeometryValidator(
                (location, radius) -> {
                    assertEquals(20.0, radius);
                    return List.of(
                            eastWestRoad(202, "Road Through Circle", 0.0),
                            northSouthRoad(101, "Road Outside Circle", 25.0));
                });

        List<ValidationIssue> issues = validator.validate(circleRegion, 0, 0);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().message().contains("Road Through Circle"));
        assertFalse(issues.getFirst().message().contains("Road Outside Circle"));
    }

    @Test
    void validate_roadTangentToCircleAtOnlyOnePointDoesNotQualify() throws ValidationException {
        GeographicalPath circleRegion =
                circleRegion(heading(4), 50L, DistanceUnits.METER);

        List<ValidationIssue> issues = validator(
                eastWestRoad(202, "Point Tangent", 50.0))
                .validate(circleRegion, 0, 0);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().message().contains(
                "no mapped roadway segment inside the TIM region"));
    }

    @ParameterizedTest
    @MethodSource("circleUnitConversions")
    void validate_circleConvertsEncodedRadiusToMeters(
            DistanceUnits units,
            double expectedRadiusMeters) throws ValidationException {
        GeographicalPath circleRegion = circleRegion(heading(0), 2L, units);
        HeadingSliceGeometryValidator validator = new HeadingSliceGeometryValidator(
                (location, radius) -> {
                    assertEquals(expectedRadiusMeters, radius, 1.0e-9);
                    return List.of(northSouthRoad(101, "Main Street", 0.0));
                });

        List<ValidationIssue> issues = validator.validate(circleRegion, 0, 0);

        assertTrue(issues.isEmpty(), issues.toString());
    }

    private static Stream<Arguments> circleUnitConversions() {
        return Stream.of(
                Arguments.of(DistanceUnits.CENTIMETER, 0.02),
                Arguments.of(DistanceUnits.CM2_5, 0.05),
                Arguments.of(DistanceUnits.DECIMETER, 0.2),
                Arguments.of(DistanceUnits.METER, 2.0),
                Arguments.of(DistanceUnits.KILOMETER, 2_000.0),
                Arguments.of(DistanceUnits.FOOT, 0.6096),
                Arguments.of(DistanceUnits.YARD, 1.8288),
                Arguments.of(DistanceUnits.MILE, 3_218.688));
    }

    private static HeadingSliceGeometryValidator validator(RoadSegment... roads) {
        return new HeadingSliceGeometryValidator(
                (location, radius) -> List.of(roads));
    }

    private static HeadingSlice heading(int... indexes) {
        HeadingSlice heading = new HeadingSlice();
        for (int index : indexes) {
            heading.set(index, true);
        }
        return heading;
    }

    private static GeographicalPath region(HeadingSlice heading) {
        return closedPathRegion(
                heading,
                xyNode(-5_000L, -5_000L),
                xyNode(10_000L, 0L),
                xyNode(0L, 10_000L),
                xyNode(-10_000L, 0L),
                xyNode(0L, -10_000L));
    }

    private static GeographicalPath nestedGeometryRegion(HeadingSlice heading) {
        return circleRegion(heading, 30L, DistanceUnits.METER);
    }

    private static GeographicalPath circleRegion(
            HeadingSlice heading,
            long radius,
            DistanceUnits units) {
        GeometricProjection geometry = new GeometricProjection();
        geometry.setDirection(heading);
        Circle circle = new Circle();
        circle.setCenter(anchor());
        circle.setRadius(new Radius_B12(radius));
        circle.setUnits(units);
        geometry.setCircle(circle);

        GeographicalPath.DescriptionChoice description = new GeographicalPath.DescriptionChoice();
        description.setGeometry(geometry);

        GeographicalPath region = new GeographicalPath();
        region.setDescription(description);
        return region;
    }

    private static GeographicalPath closedPathRegion(
            HeadingSlice heading,
            NodeXY... nodes) {
        NodeSetXY nodeSet = new NodeSetXY();
        for (NodeXY node : nodes) {
            nodeSet.add(node);
        }
        NodeListXY nodeList = new NodeListXY();
        nodeList.setNodes(nodeSet);
        OffsetSystem.OffsetChoice offset = new OffsetSystem.OffsetChoice();
        offset.setXy(nodeList);
        OffsetSystem path = new OffsetSystem();
        path.setScale(new Zoom(0L));
        path.setOffset(offset);
        GeographicalPath.DescriptionChoice description =
                new GeographicalPath.DescriptionChoice();
        description.setPath(path);

        GeographicalPath region = new GeographicalPath();
        region.setAnchor(anchor());
        region.setClosedPath(new Asn1Boolean(true));
        region.setDirection(heading);
        region.setDescription(description);
        return region;
    }

    private static NodeXY xyNode(long xCentimeters, long yCentimeters) {
        Node_XY_32b value = new Node_XY_32b();
        value.setX(new Offset_B16(xCentimeters));
        value.setY(new Offset_B16(yCentimeters));
        NodeOffsetPointXY delta = new NodeOffsetPointXY();
        delta.setNode_XY6(value);
        NodeXY node = new NodeXY();
        node.setDelta(delta);
        return node;
    }

    private static Position3D anchor() {
        Position3D anchor = new Position3D();
        anchor.setLat(new Latitude(400_000_000L));
        anchor.setLong_(new Longitude(-1_050_000_000L));
        anchor.setElevation(new Elevation(-4096L));
        return anchor;
    }

    private static RoadSegment northSouthRoad(long id, String name, double eastOffsetMeters) {
        return localRoad(
                id,
                name,
                new Coordinate(eastOffsetMeters, -100.0),
                new Coordinate(eastOffsetMeters, 100.0));
    }

    private static RoadSegment eastWestRoad(long id, String name, double northOffsetMeters) {
        return localRoad(
                id,
                name,
                new Coordinate(-100.0, northOffsetMeters),
                new Coordinate(100.0, northOffsetMeters));
    }

    private static RoadSegment roadAtBearing(long id, String name, double bearingDegrees) {
        double radians = Math.toRadians(bearingDegrees);
        double halfLengthMeters = 100.0;
        double eastOffset = halfLengthMeters * Math.sin(radians);
        double northOffset = halfLengthMeters * Math.cos(radians);
        return localRoad(
                id,
                name,
                new Coordinate(-eastOffset, -northOffset),
                new Coordinate(eastOffset, northOffset));
    }

    private static RoadSegment localRoad(
            long id,
            String name,
            Coordinate... localCoordinatesMeters) {
        List<Coordinate> wgs84Coordinates = OffsetPathDecoder.wgs84Coordinates(
                anchor(),
                localCoordinatesMeters).orElseThrow();
        return new RoadSegment(id, name, wgs84Coordinates);
    }
}
