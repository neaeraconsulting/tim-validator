package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import us.dot.its.jpo.asn.j2735.r2024.Common.Latitude;
import us.dot.its.jpo.asn.j2735.r2024.Common.Longitude;
import us.dot.its.jpo.asn.j2735.r2024.Common.Position3D;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Circle;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.DistanceUnits;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeometricProjection;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Radius_B12;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrameList;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.UniqueMSGID;
import us.dot.its.jpo.timvalidator.gnis.GnisBounds;
import us.dot.its.jpo.timvalidator.gnis.GnisFeature;
import us.dot.its.jpo.timvalidator.gnis.GnisFeatureProvider;
import us.dot.its.jpo.timvalidator.gnis.GnisLookupException;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;

class GnisPacketIdValidatorTest {

    private static final int GNIS_ID = 0x123456;

    @Test
    void validate_readsUnsignedBigEndianPrefixAndWarnsForUnknownCivilId() {
        StubProvider provider = new StubProvider(Optional.empty(), List.of());
        GnisPacketIdValidator validator = new GnisPacketIdValidator(provider);

        ValidationIssue issue = validator.validate(tim("123456000000000000", 40.0, -105.0)).getFirst();

        assertEquals(GNIS_ID, provider.requestedId);
        assertTrue(provider.requestedBounds.isEmpty());
        assertEquals(ValidationSeverity.WARNING, issue.severity());
        assertEquals("Best Practices", issue.checkName());
        assertEquals("/value/TravelerInformation/packetID", issue.path());
        assertTrue(issue.message().contains("not an approved Civil GNIS feature"));
    }

    @Test
    void validate_selectedFeatureInsideExpandedBoundsDoesNotFlag() {
        GnisFeature feature = feature(-104.6, 40.0);
        StubProvider provider = new StubProvider(Optional.of(feature), List.of(feature));
        GnisPacketIdValidator validator = new GnisPacketIdValidator(provider);

        assertTrue(validator.validate(tim("123456000000000000", 40.0, -105.0)).isEmpty());
        assertEquals(1, provider.requestedBounds.size());
        GnisBounds queried = provider.requestedBounds.getFirst();
        assertTrue(queried.minimumLongitude() < -105.5);
        assertTrue(queried.maximumLongitude() > -104.5);
    }

    @Test
    void validate_clearlyDistantFeatureWarns() {
        StubProvider provider = new StubProvider(
                Optional.of(feature(-74.0, 40.7)),
                List.of());
        GnisPacketIdValidator validator = new GnisPacketIdValidator(provider);

        ValidationIssue issue = validator.validate(
                tim("123456000000000000", 40.0, -105.0)).getFirst();

        assertTrue(issue.message().contains("geographically inconsistent with the TIM bounds"));
    }

    @Test
    void validate_intermediateDistanceWarnsThatRelationshipIsUnverified() {
        StubProvider provider = new StubProvider(
                Optional.of(feature(-102.7, 40.0)),
                List.of());
        GnisPacketIdValidator validator = new GnisPacketIdValidator(provider);

        ValidationIssue issue = validator.validate(
                tim("123456000000000000", 40.0, -105.0)).getFirst();

        assertTrue(issue.message().contains("could not be confidently verified"));
        assertTrue(issue.message().contains("expanded search area"));
    }

    @Test
    void validate_lookupFailureProducesGenericWarning() {
        GnisFeatureProvider provider = new GnisFeatureProvider() {
            @Override
            public Optional<GnisFeature> findById(int id) throws GnisLookupException {
                throw new GnisLookupException("database unavailable");
            }

            @Override
            public List<GnisFeature> findWithin(GnisBounds bounds) {
                throw new AssertionError("spatial query must not run");
            }
        };
        GnisPacketIdValidator validator = new GnisPacketIdValidator(provider);

        ValidationIssue issue = validator.validate(
                tim("123456000000000000", 40.0, -105.0)).getFirst();

        assertTrue(issue.message().contains("could not be verified against the TIM bounds"));
        assertTrue(issue.message().contains("database unavailable"));
    }

    @Test
    void validate_missingRegionGeometryProducesGenericWarning() {
        StubProvider provider = new StubProvider(
                Optional.of(feature(-105.0, 40.0)),
                List.of());
        GnisPacketIdValidator validator = new GnisPacketIdValidator(provider);
        TravelerInformation tim = new TravelerInformation();
        tim.setPacketID(new UniqueMSGID("123456000000000000"));

        ValidationIssue issue = validator.validate(tim).getFirst();

        assertTrue(issue.message().contains("TIM does not contain usable region geometry"));
        assertTrue(provider.requestedBounds.isEmpty());
    }

    @Test
    void validate_shortPacketIdIsLeftToSchemaValidation() {
        StubProvider provider = new StubProvider(Optional.empty(), List.of());
        TravelerInformation tim = new TravelerInformation();

        assertTrue(new GnisPacketIdValidator(provider).validate(tim).isEmpty());
        assertEquals(-1, provider.requestedId);
    }

    private static GnisFeature feature(double longitude, double latitude) {
        return new GnisFeature(
                GNIS_ID,
                "Civil Area",
                "Civil",
                new Coordinate(longitude, latitude));
    }

    private static TravelerInformation tim(String packetId, double latitude, double longitude) {
        TravelerInformation tim = new TravelerInformation();
        tim.setPacketID(new UniqueMSGID(packetId));

        Position3D center = new Position3D();
        center.setLat(new Latitude(Math.round(latitude * 10_000_000.0)));
        center.setLong_(new Longitude(Math.round(longitude * 10_000_000.0)));
        Circle circle = new Circle();
        circle.setCenter(center);
        circle.setRadius(new Radius_B12(1_000));
        circle.setUnits(DistanceUnits.METER);
        GeometricProjection geometry = new GeometricProjection();
        geometry.setCircle(circle);
        GeographicalPath.DescriptionChoice description =
                new GeographicalPath.DescriptionChoice();
        description.setGeometry(geometry);
        GeographicalPath region = new GeographicalPath();
        region.setDescription(description);

        TravelerDataFrame.SequenceOfRegions regions = new TravelerDataFrame.SequenceOfRegions();
        regions.add(region);
        TravelerDataFrame dataFrame = new TravelerDataFrame();
        dataFrame.setRegions(regions);
        TravelerDataFrameList dataFrames = new TravelerDataFrameList();
        dataFrames.add(dataFrame);
        tim.setDataFrames(dataFrames);
        return tim;
    }

    private static final class StubProvider implements GnisFeatureProvider {

        private final Optional<GnisFeature> lookup;
        private final List<GnisFeature> spatialResults;
        private final List<GnisBounds> requestedBounds = new ArrayList<>();
        private int requestedId = -1;

        private StubProvider(
                Optional<GnisFeature> lookup,
                List<GnisFeature> spatialResults) {
            this.lookup = lookup;
            this.spatialResults = spatialResults;
        }

        @Override
        public Optional<GnisFeature> findById(int id) {
            requestedId = id;
            return lookup;
        }

        @Override
        public List<GnisFeature> findWithin(GnisBounds bounds) {
            requestedBounds.add(bounds);
            return spatialResults;
        }
    }
}
