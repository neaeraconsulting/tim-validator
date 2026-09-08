package us.dot.its.jpo.timvalidator.gnis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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
    void loader_parsesQuotedNamesAndReportsInvalidFeatureData() throws Exception {
        CsvGnisFeatureProvider testProvider = providerWithInvalidFeature();

        GnisFeature valid = testProvider.findById(198131).orElseThrow();
        GnisLookupException exception = assertThrows(
                GnisLookupException.class,
                () -> testProvider.findById(0x1000000));

        assertEquals("Denver, \"County\"", valid.name());
        assertTrue(exception.getMessage().contains("16777216"));
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

    @Test
    void emptyCsv_reportsLookupFailure() throws Exception {
        Path csv = writeCsv();
        CsvGnisFeatureProvider testProvider = new CsvGnisFeatureProvider(csv);

        GnisLookupException exception = assertThrows(
                GnisLookupException.class,
                () -> testProvider.findById(198131));

        assertTrue(exception.getMessage().contains("does not contain any valid features"));
    }

    @Test
    void loader_rejectsNonCivilAndUnknownLocationFeatures() throws Exception {
        Path csv = writeCsv(
                "199999,Valid Feature,Civil,Colorado,39.6,-104.8",
                "200000,Not Civil,Populated Place,Colorado,39.7,-104.9",
                "200001,Unknown Location,Civil,Colorado,0.0,0.0");
        CsvGnisFeatureProvider testProvider = new CsvGnisFeatureProvider(csv);

        GnisLookupException nonCivil = assertThrows(
                GnisLookupException.class,
                () -> testProvider.findById(200000));
        GnisLookupException unknownLocation = assertThrows(
                GnisLookupException.class,
                () -> testProvider.findById(200001));

        assertTrue(nonCivil.getMessage().contains("feature_class must be Civil"));
        assertTrue(unknownLocation.getMessage().contains("unknown-location value"));
    }

    @Test
    void loader_keepsDuplicateFeatureIdRejectedAfterLaterRows() throws Exception {
        Path csv = writeCsv(
                "199999,Valid Feature,Civil,Colorado,39.6,-104.8",
                "200000,First,Civil,Colorado,39.7,-104.9",
                "200000,Second,Civil,Colorado,39.8,-104.8",
                "200000,Third,Civil,Colorado,39.9,-104.7");
        CsvGnisFeatureProvider testProvider = new CsvGnisFeatureProvider(csv);

        GnisLookupException exception = assertThrows(
                GnisLookupException.class,
                () -> testProvider.findById(200000));

        assertTrue(exception.getMessage().contains("feature_id is duplicated"));
    }

    private CsvGnisFeatureProvider providerWithInvalidFeature() throws Exception {
        return new CsvGnisFeatureProvider(writeCsv(
                "198131,\"Denver, \"\"County\"\"\",Civil,Colorado,39.7619791,-104.8757684",
                "16777216,Invalid ID,Civil,Colorado,39.7,-104.9"));
    }

    private Path writeCsv(String... rows) throws Exception {
        Path csv = temporaryDirectory.resolve("features-" + System.nanoTime() + ".csv");
        Files.writeString(csv,
                "feature_id,feature_name,feature_class,state_name,prim_lat_dec,prim_long_dec\n"
                        + String.join("\n", rows)
                        + "\n");
        return csv;
    }
}
