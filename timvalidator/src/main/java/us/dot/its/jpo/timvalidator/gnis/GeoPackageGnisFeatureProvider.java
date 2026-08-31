package us.dot.its.jpo.timvalidator.gnis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.locationtech.jts.geom.CoordinateXY;
import org.sqlite.SQLiteConfig;

/** Reads approved Civil GNIS deployment-area features from a GeoPackage. */
public final class GeoPackageGnisFeatureProvider implements GnisFeatureProvider {

    private static final String RESOURCE_NAME = "civil_gnis_deployment_areas.gpkg";
    private static final String FIND_BY_ID_SQL = """
            SELECT feature_id, feature_name, feature_class,
                   prim_long_dec, prim_lat_dec
            FROM gnis_deployment_areas
            WHERE feature_id = ?
            """;
    private static final String FIND_WITHIN_SQL = """
            SELECT feature.feature_id, feature.feature_name, feature.feature_class,
                   feature.prim_long_dec, feature.prim_lat_dec
            FROM rtree_gnis_deployment_areas_geom AS bounds
            JOIN gnis_deployment_areas AS feature ON feature.fid = bounds.id
            WHERE bounds.maxx >= ? AND bounds.minx <= ?
              AND bounds.maxy >= ? AND bounds.miny <= ?
            """;

    private final Path databasePath;

    /** Uses the Civil GNIS GeoPackage packaged inside the timvalidator JAR. */
    public GeoPackageGnisFeatureProvider() {
        this.databasePath = null;
    }

    /** Uses a GeoPackage at an explicit path, primarily for tests and custom datasets. */
    public GeoPackageGnisFeatureProvider(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public Optional<GnisFeature> findById(int gnisId) throws GnisLookupException {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_BY_ID_SQL)) {
            statement.setInt(1, gnisId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next()
                        ? Optional.of(readFeature(results))
                        : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new GnisLookupException("Unable to query the Civil GNIS GeoPackage", ex);
        }
    }

    @Override
    public List<GnisFeature> findWithin(GnisBounds bounds) throws GnisLookupException {
        Objects.requireNonNull(bounds, "bounds");
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_WITHIN_SQL)) {
            statement.setDouble(1, bounds.minimumLongitude());
            statement.setDouble(2, bounds.maximumLongitude());
            statement.setDouble(3, bounds.minimumLatitude());
            statement.setDouble(4, bounds.maximumLatitude());
            try (ResultSet results = statement.executeQuery()) {
                List<GnisFeature> features = new ArrayList<>();
                while (results.next()) {
                    features.add(readFeature(results));
                }
                return List.copyOf(features);
            }
        } catch (SQLException ex) {
            throw new GnisLookupException("Unable to query the Civil GNIS GeoPackage", ex);
        }
    }

    private Connection openConnection() throws SQLException, GnisLookupException {
        Path path = databasePath == null
                ? PackagedDatabase.path()
                : databasePath;
        if (!Files.isRegularFile(path)) {
            throw new GnisLookupException("Civil GNIS GeoPackage does not exist: " + path);
        }
        SQLiteConfig configuration = new SQLiteConfig();
        configuration.setReadOnly(true);
        return DriverManager.getConnection(
                "jdbc:sqlite:" + path,
                configuration.toProperties());
    }

    private static GnisFeature readFeature(ResultSet results) throws SQLException {
        return new GnisFeature(
                results.getInt("feature_id"),
                results.getString("feature_name"),
                results.getString("feature_class"),
                new CoordinateXY(
                        results.getDouble("prim_long_dec"),
                        results.getDouble("prim_lat_dec")));
    }

    private static final class PackagedDatabase {

        private static final Path PATH = extract();

        private PackagedDatabase() {
        }

        private static Path path() throws GnisLookupException {
            if (PATH == null) {
                throw new GnisLookupException(
                        "Packaged Civil GNIS GeoPackage could not be loaded");
            }
            return PATH;
        }

        private static Path extract() {
            try (InputStream source = GeoPackageGnisFeatureProvider.class
                    .getResourceAsStream(RESOURCE_NAME)) {
                if (source == null) {
                    return null;
                }
                Path extracted = Files.createTempFile("timvalidator-civil-gnis-", ".gpkg");
                Files.copy(source, extracted, StandardCopyOption.REPLACE_EXISTING);
                extracted.toFile().deleteOnExit();
                return extracted;
            } catch (IOException ex) {
                return null;
            }
        }
    }
}
