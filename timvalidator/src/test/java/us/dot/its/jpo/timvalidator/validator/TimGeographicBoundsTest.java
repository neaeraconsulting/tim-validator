package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.timvalidator.gnis.GnisBounds;

class TimGeographicBoundsTest {

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

        assertEquals(1, tim.getDataFrames().size());
        assertEquals(1, tim.getDataFrames().getFirst().getRegions().size());
        assertAllRegionGeometryIsInsideCombinedBounds(tim);
    }

    @Test
    void from_readsCircleJerFixture() {
        TravelerInformation tim = fixture("SingleRegionCircleTIM.json");
        TimGeographicBounds bounds = TimGeographicBounds.from(tim).orElseThrow();
        GeographicalPath region = tim.getDataFrames().getFirst().getRegions().getFirst();
        Coordinate center = OffsetPathDecoder.wgs84Coordinate(
                region.getDescription().getGeometry().getCircle().getCenter()).orElseThrow();

        assertEquals(0.0, bounds.distanceMeters(center), 0.01);
        assertTrue(bounds.diagonalMeters() > 700.0);
        assertTrue(bounds.diagonalMeters() < 720.0);
    }

    @Test
    void from_readsSingleOffsetPathJerFixture() {
        TravelerInformation tim = fixture("SingleRegionPathOffsetTIM.json");

        assertEquals(1, tim.getDataFrames().size());
        assertEquals(1, tim.getDataFrames().getFirst().getRegions().size());
        assertAllRegionGeometryIsInsideCombinedBounds(tim);
    }

    @Test
    void from_combinesAllRegionsInJerFixture() {
        TravelerInformation tim = fixture("MultiRegionPathOffsetTIM.json");

        assertEquals(1, tim.getDataFrames().size());
        assertEquals(2, tim.getDataFrames().getFirst().getRegions().size());
        assertAllRegionGeometryIsInsideCombinedBounds(tim);
    }

    @Test
    void from_combinesAllDataFramesInJerFixture() {
        TravelerInformation tim = fixture("MultiDataFrameOffsetTim.json");

        assertEquals(2, tim.getDataFrames().size());
        assertAllRegionGeometryIsInsideCombinedBounds(tim);
    }

    private static TravelerInformation fixture(String fileName) {
        return GnisTimTestFixtures.travelerInformation(fileName);
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
