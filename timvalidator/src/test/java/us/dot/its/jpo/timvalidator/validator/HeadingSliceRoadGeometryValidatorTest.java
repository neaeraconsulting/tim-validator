package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import us.dot.its.jpo.asn.j2735.r2024.Common.Elevation;
import us.dot.its.jpo.asn.j2735.r2024.Common.HeadingSlice;
import us.dot.its.jpo.asn.j2735.r2024.Common.Latitude;
import us.dot.its.jpo.asn.j2735.r2024.Common.Longitude;
import us.dot.its.jpo.asn.j2735.r2024.Common.Position3D;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeometricProjection;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.road.RoadGeometryProvider;
import us.dot.its.jpo.timvalidator.road.RoadSegment;

class HeadingSliceRoadGeometryValidatorTest {

    private static final double LATITUDE = 40.0;
    private static final double LONGITUDE = -105.0;
    private static final double SEARCH_RADIUS_METERS = 30.0;

    @Test
    void validate_centerSliceTangentToClosestRoadProducesNoIssue() {
        GeographicalPath region = region(heading(0));
        HeadingSliceRoadGeometryValidator validator = validator(northSouthRoad(101, "Main Street", 0.0));

        List<ValidationIssue> issues = validator.validate(region, 2, 3);

        assertTrue(issues.isEmpty());
    }

    @Test
    void validate_oppositeCentersAreBothTangentToTheSameRoadAxis() {
        GeographicalPath region = region(heading(0, 8));
        HeadingSliceRoadGeometryValidator validator = validator(northSouthRoad(101, "Main Street", 0.0));

        List<ValidationIssue> issues = validator.validate(region, 0, 0);

        assertTrue(issues.isEmpty());
    }

    @Test
    void validate_wrappedThreeSliceRangeUsesItsCenterSlice() {
        GeographicalPath region = region(heading(15, 0, 1));
        HeadingSliceRoadGeometryValidator validator = validator(northSouthRoad(101, "Main Street", 0.0));

        List<ValidationIssue> issues = validator.validate(region, 0, 0);

        assertTrue(issues.isEmpty());
    }

    @Test
    void validate_nonTangentCenterReturnsStructuredWarning() {
        GeographicalPath region = region(heading(0));
        HeadingSliceRoadGeometryValidator validator = validator(eastWestRoad(202, "Broadway", 0.0));

        List<ValidationIssue> issues = validator.validate(region, 2, 3);

        assertEquals(1, issues.size());
        ValidationIssue issue = issues.getFirst();
        assertEquals(ValidationSeverity.WARNING, issue.severity());
        assertEquals("Best Practices", issue.checkName());
        assertEquals("/value/TravelerInformation/dataFrames/2/regions/3/direction", issue.path());
        assertTrue(issue.message().contains("not tangent"));
        assertTrue(issue.message().contains("OSM way 202 (Broadway)"));
    }

    @Test
    void validate_nestedGeometricProjectionHeadingUsesNestedPath() {
        GeographicalPath region = nestedGeometryRegion(heading(0));
        HeadingSliceRoadGeometryValidator validator = validator(eastWestRoad(202, null, 0.0));

        List<ValidationIssue> issues = validator.validate(region, 0, 1);

        assertEquals(1, issues.size());
        assertEquals(
                "/value/TravelerInformation/dataFrames/0/regions/1/description/geometry/direction",
                issues.getFirst().path());
    }

    @Test
    void validate_missingNoHeadingAndAllHeadingsDoNotCallProvider() {
        AtomicInteger calls = new AtomicInteger();
        RoadGeometryProvider provider = (location, radius) -> {
            calls.incrementAndGet();
            return List.of(northSouthRoad(101, null, 0.0));
        };
        HeadingSliceRoadGeometryValidator validator =
                new HeadingSliceRoadGeometryValidator(provider, SEARCH_RADIUS_METERS);

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
    void validate_evenWidthHeadingRangeUsesMidpointAndCallsProvider() {
        AtomicInteger calls = new AtomicInteger();
        HeadingSliceRoadGeometryValidator validator = new HeadingSliceRoadGeometryValidator(
                (location, radius) -> {
                    calls.incrementAndGet();
                    return List.of(northSouthRoad(101, "Main Street", 0.0));
                },
                SEARCH_RADIUS_METERS);

        List<ValidationIssue> issues = validator.validate(region(heading(0, 1)), 0, 0);

        assertTrue(issues.isEmpty(), issues.toString());
        assertEquals(1, calls.get());
    }

    @Test
    void validate_wrappedEvenWidthRangeUsesCircularMidpoint() {
        HeadingSliceRoadGeometryValidator validator =
                validator(northSouthRoad(101, "Main Street", 0.0));

        List<ValidationIssue> issues =
                validator.validate(region(heading(15, 0)), 0, 0);

        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void validate_multipleEvenWidthRangesAreCheckedIndependently() {
        HeadingSliceRoadGeometryValidator validator = validator(
                northSouthRoad(101, "Main Street", 0.0),
                eastWestRoad(202, "Broadway", 0.0));

        List<ValidationIssue> issues =
                validator.validate(region(heading(0, 1, 4, 5)), 0, 0);

        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void validate_oddWidthRangeAlsoUsesPlusOrMinusOneSliceTolerance() {
        HeadingSliceRoadGeometryValidator validator =
                validator(roadAtBearing(303, "Diagonal Road", 32.0));

        List<ValidationIssue> issues =
                validator.validate(region(heading(0)), 0, 0);

        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void validate_providerFailureReturnsNonBlockingWarning() {
        HeadingSliceRoadGeometryValidator validator = new HeadingSliceRoadGeometryValidator(
                (location, radius) -> {
                    throw new IllegalStateException("service unavailable");
                },
                SEARCH_RADIUS_METERS);

        List<ValidationIssue> issues = validator.validate(region(heading(0)), 0, 0);

        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.WARNING, issues.getFirst().severity());
        assertTrue(issues.getFirst().message().contains("service unavailable"));
    }

    @Test
    void validate_noNearbyRoadReturnsWarning() {
        HeadingSliceRoadGeometryValidator validator =
                new HeadingSliceRoadGeometryValidator(
                        (location, radius) -> List.of(),
                        SEARCH_RADIUS_METERS);

        List<ValidationIssue> issues = validator.validate(region(heading(0)), 0, 0);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().message().contains("no mapped roadway"));
    }

    @Test
    void validate_headingMayMatchEitherRoadAtPerpendicularIntersection() {
        HeadingSliceRoadGeometryValidator validator = validator(
                northSouthRoad(101, "Main Street", 0.0),
                eastWestRoad(202, "Broadway", 0.0));

        List<ValidationIssue> issues = validator.validate(region(heading(0)), 0, 0);

        assertTrue(issues.isEmpty());
    }

    @Test
    void validate_eachHeadingRangeMayMatchADifferentIntersectionRoad() {
        HeadingSliceRoadGeometryValidator validator = validator(
                northSouthRoad(101, "Main Street", 0.0),
                eastWestRoad(202, "Broadway", 0.0));

        List<ValidationIssue> issues = validator.validate(region(heading(0, 4)), 0, 0);

        assertTrue(issues.isEmpty());
    }

    @Test
    void validate_roadOutsideCandidateToleranceDoesNotSatisfyHeading() {
        HeadingSliceRoadGeometryValidator validator = validatorWithTolerance(
                8.0,
                eastWestRoad(202, "Closest Road", 0.0),
                northSouthRoad(101, "Road Outside Tolerance", 9.0));

        List<ValidationIssue> issues = validator.validate(region(heading(0)), 0, 0);

        assertEquals(1, issues.size());
        assertTrue(issues.getFirst().message().contains("not tangent to any mapped roadway candidate"));
        assertTrue(issues.getFirst().message().contains("OSM way 202 (Closest Road)"));
        assertFalse(issues.getFirst().message().contains("Road Outside Tolerance"));
    }

    @Test
    void validate_configuredCandidateToleranceIncludesAdditionalRoad() {
        HeadingSliceRoadGeometryValidator validator = validatorWithTolerance(
                10.0,
                eastWestRoad(202, "Closest Road", 0.0),
                northSouthRoad(101, "Cross Street", 7.0));

        List<ValidationIssue> issues = validator.validate(region(heading(0)), 0, 0);

        assertTrue(issues.isEmpty(), issues.toString());
    }

    @Test
    void validate_usesClosestRoadRatherThanFirstProviderResult() {
        HeadingSliceRoadGeometryValidator validator = validator(
                eastWestRoad(202, "Distant Road", 10.0),
                northSouthRoad(101, "Main Street", 0.0));

        List<ValidationIssue> issues = validator.validate(region(heading(0)), 0, 0);

        assertTrue(issues.isEmpty());
    }

    private static HeadingSliceRoadGeometryValidator validator(RoadSegment... roads) {
        return new HeadingSliceRoadGeometryValidator(
                (location, radius) -> List.of(roads),
                SEARCH_RADIUS_METERS);
    }

    private static HeadingSliceRoadGeometryValidator validatorWithTolerance(
            double candidateDistanceToleranceMeters,
            RoadSegment... roads) {
        return new HeadingSliceRoadGeometryValidator(
                (location, radius) -> List.of(roads),
                SEARCH_RADIUS_METERS,
                candidateDistanceToleranceMeters);
    }

    private static HeadingSlice heading(int... indexes) {
        HeadingSlice heading = new HeadingSlice();
        for (int index : indexes) {
            heading.set(index, true);
        }
        return heading;
    }

    private static GeographicalPath region(HeadingSlice heading) {
        GeographicalPath region = new GeographicalPath();
        region.setAnchor(anchor());
        region.setDirection(heading);
        return region;
    }

    private static GeographicalPath nestedGeometryRegion(HeadingSlice heading) {
        GeometricProjection geometry = new GeometricProjection();
        geometry.setDirection(heading);

        GeographicalPath.DescriptionChoice description = new GeographicalPath.DescriptionChoice();
        description.setGeometry(geometry);

        GeographicalPath region = new GeographicalPath();
        region.setAnchor(anchor());
        region.setDescription(description);
        return region;
    }

    private static Position3D anchor() {
        Position3D anchor = new Position3D();
        anchor.setLat(new Latitude(400_000_000L));
        anchor.setLong_(new Longitude(-1_050_000_000L));
        anchor.setElevation(new Elevation(-4096L));
        return anchor;
    }

    private static RoadSegment northSouthRoad(long id, String name, double eastOffsetMeters) {
        double longitudeOffset = eastOffsetMeters / 85_180.0;
        return new RoadSegment(
                id,
                name,
                List.of(
                        new Coordinate(LONGITUDE + longitudeOffset, LATITUDE - 0.001),
                        new Coordinate(LONGITUDE + longitudeOffset, LATITUDE + 0.001)));
    }

    private static RoadSegment eastWestRoad(long id, String name, double northOffsetMeters) {
        double latitudeOffset = northOffsetMeters / 111_195.0;
        return new RoadSegment(
                id,
                name,
                List.of(
                        new Coordinate(LONGITUDE - 0.001, LATITUDE + latitudeOffset),
                        new Coordinate(LONGITUDE + 0.001, LATITUDE + latitudeOffset)));
    }

    private static RoadSegment roadAtBearing(long id, String name, double bearingDegrees) {
        double radians = Math.toRadians(bearingDegrees);
        double halfLengthMeters = 100.0;
        double latitudeOffset = halfLengthMeters * Math.cos(radians) / 111_195.0;
        double longitudeOffset = halfLengthMeters * Math.sin(radians) / 85_180.0;
        return new RoadSegment(
                id,
                name,
                List.of(
                        new Coordinate(
                                LONGITUDE - longitudeOffset,
                                LATITUDE - latitudeOffset),
                        new Coordinate(
                                LONGITUDE + longitudeOffset,
                                LATITUDE + latitudeOffset)));
    }
}
