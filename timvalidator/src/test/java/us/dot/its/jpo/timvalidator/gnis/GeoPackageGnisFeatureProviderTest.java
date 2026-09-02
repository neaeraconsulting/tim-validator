package us.dot.its.jpo.timvalidator.gnis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoPackageGnisFeatureProviderTest {

    @TempDir
    Path temporaryDirectory;

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

    @Test
    void findById_reportsInvalidFeatureDataAsLookupFailure() throws Exception {
        GeoPackageGnisFeatureProvider testProvider = providerWithInvalidFeature();

        GnisLookupException exception = assertThrows(
                GnisLookupException.class,
                () -> testProvider.findById(0x1000000));

        assertTrue(exception.getMessage().contains("16777216"));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void findWithin_skipsInvalidFeatureAndReturnsRemainingFeatures() throws Exception {
        GeoPackageGnisFeatureProvider testProvider = providerWithInvalidFeature();

        List<GnisFeature> features = testProvider.findWithin(
                new GnisBounds(-105.1, -104.7, 39.6, 39.9));

        assertEquals(List.of(198131), features.stream().map(GnisFeature::id).toList());
    }

    private GeoPackageGnisFeatureProvider providerWithInvalidFeature() throws Exception {
        Path database = temporaryDirectory.resolve("invalid-feature.gpkg");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE gnis_deployment_areas (
                        fid INTEGER PRIMARY KEY,
                        feature_id INTEGER,
                        feature_name TEXT,
                        feature_class TEXT,
                        prim_long_dec REAL,
                        prim_lat_dec REAL)
                    """);
            statement.execute("""
                    CREATE VIRTUAL TABLE rtree_gnis_deployment_areas_geom
                    USING rtree(id, minx, maxx, miny, maxy)
                    """);
            statement.execute("""
                    INSERT INTO gnis_deployment_areas VALUES
                        (1, 198131, 'Denver County', 'Civil', -104.8757684, 39.7619791),
                        (2, 16777216, 'Invalid ID', 'Civil', -104.9, 39.7)
                    """);
            statement.execute("""
                    INSERT INTO rtree_gnis_deployment_areas_geom VALUES
                        (1, -104.8757684, -104.8757684, 39.7619791, 39.7619791),
                        (2, -104.9, -104.9, 39.7, 39.7)
                    """);
        }
        return new GeoPackageGnisFeatureProvider(database);
    }
}
