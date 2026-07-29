package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrameList;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.road.RoadSegment;

class BestPracticesRoadGeometryIntegrationTest {

    @Test
    void validateAndCollectIssues_returnsHeadingMismatchAsValidationIssue() {
        BestPracticesValidator validator = new BestPracticesValidator(
                (location, radius) -> List.of(new RoadSegment(
                        202L,
                        "Broadway",
                        List.of(
                                new Coordinate(-105.001, 40.0),
                                new Coordinate(-104.999, 40.0)))),
                30.0);

        List<ValidationIssue> issues = validator.validateAndCollectIssues(messageWithHeading(0));

        assertEquals(1, issues.size());
        assertEquals(ValidationSeverity.WARNING, issues.getFirst().severity());
        assertEquals("Best Practices", issues.getFirst().checkName());
        assertEquals(
                "/value/TravelerInformation/dataFrames/0/regions/0/direction",
                issues.getFirst().path());
        assertTrue(issues.getFirst().message().contains("not tangent"));
    }

    @Test
    void validateAndCollectIssues_missingOptionalTimDoesNotCallRoadProvider() {
        AtomicInteger providerCalls = new AtomicInteger();
        BestPracticesValidator validator = new BestPracticesValidator(
                (location, radius) -> {
                    providerCalls.incrementAndGet();
                    return List.of();
                },
                30.0);

        List<ValidationIssue> issues =
                validator.validateAndCollectIssues(new TravelerInformationMessageFrame());

        assertEquals(0, providerCalls.get());
        assertTrue(issues.stream().anyMatch(issue ->
                issue.message().contains("not a TravelerInformationMessageFrame")));
    }

    private static TravelerInformationMessageFrame messageWithHeading(int headingIndex) {
        HeadingSlice heading = new HeadingSlice();
        heading.set(headingIndex, true);

        Position3D anchor = new Position3D();
        anchor.setLat(new Latitude(400_000_000L));
        anchor.setLong_(new Longitude(-1_050_000_000L));
        anchor.setElevation(new Elevation(-4096L));

        GeographicalPath region = new GeographicalPath();
        region.setAnchor(anchor);
        region.setDirection(heading);

        TravelerDataFrame.SequenceOfRegions regions = new TravelerDataFrame.SequenceOfRegions();
        regions.add(region);

        TravelerDataFrame dataFrame = new TravelerDataFrame();
        dataFrame.setRegions(regions);

        TravelerDataFrameList dataFrames = new TravelerDataFrameList();
        dataFrames.add(dataFrame);

        TravelerInformation tim = new TravelerInformation();
        tim.setDataFrames(dataFrames);

        TravelerInformationMessageFrame message = new TravelerInformationMessageFrame();
        message.setValue(tim);
        return message;
    }
}
