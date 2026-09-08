package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
class TimGeographicBoundsTest {

    private static final double COORDINATE_TOLERANCE = 0.000001;

    @Test
    void expansionUsesFloorScaleAndCap() {
        assertEquals(50_000.0, GnisPacketIdValidator.expansionMeters(10_000.0));
        assertEquals(100_000.0, GnisPacketIdValidator.expansionMeters(500_000.0));
        assertEquals(150_000.0, GnisPacketIdValidator.expansionMeters(1_000_000.0));
    }

    @Test
    void expandedBoundsSplitAcrossAntimeridian() {
        TimGeographicBounds bounds = new TimGeographicBounds(
                179.8,
                180.2,
                40.0,
                40.1);

        List<GnisBounds> expanded = bounds.expanded(50_000.0);

        assertEquals(2, expanded.size());
        assertEquals(180.0, expanded.getFirst().maximumLongitude());
        assertEquals(-180.0, expanded.getLast().minimumLongitude());
        assertTrue(expanded.getFirst().contains(179.9, 40.0));
        assertTrue(expanded.getLast().contains(-179.9, 40.0));
    }

    @Test
    void from_readsClosedPathJerFixture() {
        TravelerInformation tim = fixture("ClosedPathTim.json");
        GeographicalPath region = tim.getDataFrames().getFirst().getRegions().getFirst();

        assertEquals(1, tim.getDataFrames().size());
        assertEquals(1, tim.getDataFrames().getFirst().getRegions().size());
        assertTrue(region.getClosedPath().getValue());
        assertNotNull(region.getDescription().getPath().getOffset().getXy());
        assertBounds(
                tim,
                -84.40274855530508,
                -84.40267213652308,
                33.75644382345412,
                33.75692778458423);
        assertAllRegionGeometryIsInsideCombinedBounds(tim);
    }

    @Test
    void from_readsCircleJerFixture() {
        TravelerInformation tim = fixture("SingleRegionCircleTIM.json");
        TimGeographicBounds bounds = TimGeographicBounds.from(tim).orElseThrow();
        GeographicalPath region = tim.getDataFrames().getFirst().getRegions().getFirst();
        Coordinate center = OffsetPathDecoder.wgs84Coordinate(
                region.getDescription().getGeometry().getCircle().getCenter()).orElseThrow();

        assertNotNull(region.getDescription().getGeometry().getCircle());
        assertBounds(
                tim,
                -74.88005185706483,
                -74.87414814293517,
                40.55754866830756,
                40.5620513308123);
        assertEquals(0.0, bounds.distanceMeters(center), 0.01);
        assertTrue(bounds.diagonalMeters() > 700.0);
        assertTrue(bounds.diagonalMeters() < 720.0);
    }

    @Test
    void from_readsSingleOffsetPathJerFixture() {
        TravelerInformation tim = fixture("SingleRegionPathOffsetTIM.json");
        GeographicalPath region = tim.getDataFrames().getFirst().getRegions().getFirst();

        assertEquals(1, tim.getDataFrames().size());
        assertEquals(1, tim.getDataFrames().getFirst().getRegions().size());
        assertNotNull(region.getDescription().getPath().getOffset().getLl());
        assertBounds(
                tim,
                -104.9701633,
                -104.9686333,
                40.4736687,
                40.4746385);
        assertAllRegionGeometryIsInsideCombinedBounds(tim);
    }

    @Test
    void from_combinesAllRegionsInJerFixture() {
        TravelerInformation tim = fixture("MultiRegionPathOffsetTIM.json");

        assertEquals(1, tim.getDataFrames().size());
        assertEquals(2, tim.getDataFrames().getFirst().getRegions().size());
        assertTrue(tim.getDataFrames().getFirst().getRegions().stream().allMatch(region ->
                region.getDescription().getPath().getOffset().getLl() != null));
        assertBounds(
                tim,
                -104.6531747,
                -104.6444607,
                41.1514363,
                41.1538749);
        assertAllRegionGeometryIsInsideCombinedBounds(tim);
    }

    @Test
    void from_combinesAllDataFramesInJerFixture() {
        TravelerInformation tim = fixture("MultiDataFrameOffsetTim.json");

        assertEquals(2, tim.getDataFrames().size());
        assertTrue(tim.getDataFrames().stream().allMatch(dataFrame ->
                dataFrame.getRegions().size() == 1
                        && dataFrame.getRegions().getFirst()
                                .getDescription().getPath().getOffset().getLl() != null));
        assertBounds(
                tim,
                -104.6531747,
                -104.6476863,
                41.1533558,
                41.1538749);
        assertAllRegionGeometryIsInsideCombinedBounds(tim);
    }

    private static TravelerInformation fixture(String fileName) {
        return GnisTimTestFixtures.travelerInformation(fileName);
    }

    private static void assertBounds(
            TravelerInformation tim,
            double minimumLongitude,
            double maximumLongitude,
            double minimumLatitude,
            double maximumLatitude) {
        TimGeographicBounds bounds = TimGeographicBounds.from(tim).orElseThrow();
        assertEquals(minimumLongitude, bounds.minimumLongitude(), COORDINATE_TOLERANCE);
        assertEquals(maximumLongitude, bounds.maximumLongitude(), COORDINATE_TOLERANCE);
        assertEquals(minimumLatitude, bounds.minimumLatitude(), COORDINATE_TOLERANCE);
        assertEquals(maximumLatitude, bounds.maximumLatitude(), COORDINATE_TOLERANCE);
    }

    private static void assertAllRegionGeometryIsInsideCombinedBounds(
            TravelerInformation tim) {
        TimGeographicBounds bounds = TimGeographicBounds.from(tim).orElseThrow();
        int decodedRegions = 0;
        int totalRegions = 0;
        for (TravelerDataFrame dataFrame : tim.getDataFrames()) {
            for (GeographicalPath region : dataFrame.getRegions()) {
                totalRegions++;
                OffsetPathDecoder.DecodeResult decoded = OffsetPathDecoder.decode(region);
                assertTrue(decoded.decoded(), decoded.failureReason());
                List<Coordinate> coordinates = OffsetPathDecoder.wgs84Coordinates(
                        region,
                        decoded.path().nodes()).orElseThrow();
                assertFalse(coordinates.isEmpty());
                for (Coordinate coordinate : coordinates) {
                    assertEquals(0.0, bounds.distanceMeters(coordinate), 0.01);
                }
                decodedRegions++;
            }
        }
        assertTrue(totalRegions > 0);
        assertEquals(totalRegions, decodedRegions);
    }
}
