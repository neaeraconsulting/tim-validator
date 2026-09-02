package us.dot.its.jpo.timvalidator.gnis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvGnisFeatureProviderTest {

    @TempDir
    Path temporaryDirectory;

    private final CsvGnisFeatureProvider provider = new CsvGnisFeatureProvider();

    @Test
    void findById_readsCivilFeatureFromPackagedCsv() throws Exception {
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
    void findWithin_usesInMemorySpatialIndex() throws Exception {
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

    @Test
    void loader_parsesQuotedNamesAndReportsInvalidFeatureData() throws Exception {
        CsvGnisFeatureProvider testProvider = providerWithInvalidFeature();

        GnisFeature valid = testProvider.findById(198131).orElseThrow();
        GnisLookupException exception = assertThrows(
                GnisLookupException.class,
                () -> testProvider.findById(0x1000000));

        assertEquals("Denver, County", valid.name());
        assertTrue(exception.getMessage().contains("16777216"));
    }

    @Test
    void findWithin_skipsInvalidFeatureAndReturnsRemainingFeatures() throws Exception {
        CsvGnisFeatureProvider testProvider = providerWithInvalidFeature();

        List<GnisFeature> features = testProvider.findWithin(
                new GnisBounds(-105.1, -104.7, 39.6, 39.9));

        assertEquals(List.of(198131), features.stream().map(GnisFeature::id).toList());
    }

    @Test
    void missingCsv_reportsOriginalIoFailure() {
        Path missing = temporaryDirectory.resolve("missing.csv");
        CsvGnisFeatureProvider testProvider = new CsvGnisFeatureProvider(missing);

        GnisLookupException exception = assertThrows(
                GnisLookupException.class,
                () -> testProvider.findById(198131));

        assertTrue(exception.getMessage().contains(missing.toString()));
        assertTrue(exception.getCause() instanceof java.io.IOException);
    }

    @Test
    void invalidHeader_reportsLookupFailure() throws Exception {
        Path csv = temporaryDirectory.resolve("invalid-header.csv");
        Files.writeString(csv, "feature_id,feature_name\n");
        CsvGnisFeatureProvider testProvider = new CsvGnisFeatureProvider(csv);

        GnisLookupException exception = assertThrows(
                GnisLookupException.class,
                () -> testProvider.findById(198131));

        assertTrue(exception.getMessage().contains("unexpected header"));
    }

    private CsvGnisFeatureProvider providerWithInvalidFeature() throws Exception {
        Path csv = temporaryDirectory.resolve("invalid-feature.csv");
        Files.writeString(csv, """
                feature_id,feature_name,feature_class,state_name,prim_lat_dec,prim_long_dec
                198131,"Denver, County",Civil,Colorado,39.7619791,-104.8757684
                16777216,Invalid ID,Civil,Colorado,39.7,-104.9
                """);
        return new CsvGnisFeatureProvider(csv);
    }
}
