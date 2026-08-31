package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

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
}
