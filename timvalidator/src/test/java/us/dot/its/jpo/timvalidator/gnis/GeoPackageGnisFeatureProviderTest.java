package us.dot.its.jpo.timvalidator.gnis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class GeoPackageGnisFeatureProviderTest {

    private final GeoPackageGnisFeatureProvider provider =
            new GeoPackageGnisFeatureProvider();

    @Test
    void findById_readsCivilFeatureFromPackagedGeoPackage() throws Exception {
        GnisFeature feature = provider.findById(198131).orElseThrow();

        assertEquals("Denver County", feature.name());
        assertEquals("Civil", feature.featureClass());
        assertEquals(-104.8757684, feature.location().getX());
        assertEquals(39.7619791, feature.location().getY());
    }

    @Test
    void findById_returnsEmptyForUnknownFeature() throws Exception {
        assertTrue(provider.findById(0).isEmpty());
    }

    @Test
    void findWithin_usesSpatialBounds() throws Exception {
        List<GnisFeature> features = provider.findWithin(
                new GnisBounds(-105.1, -104.7, 39.6, 39.9));

        assertFalse(features.isEmpty());
        assertTrue(features.stream().anyMatch(feature -> feature.id() == 198131));
        assertTrue(features.stream().allMatch(feature ->
                feature.location().getX() >= -105.1
                        && feature.location().getX() <= -104.7
                        && feature.location().getY() >= 39.6
                        && feature.location().getY() <= 39.9));
    }
}
