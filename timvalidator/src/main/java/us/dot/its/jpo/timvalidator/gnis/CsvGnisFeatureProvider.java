package us.dot.its.jpo.timvalidator.gnis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.strtree.STRtree;

/** Reads approved Civil GNIS deployment-area features from an in-memory CSV dataset. */
public final class CsvGnisFeatureProvider implements GnisFeatureProvider {

    private static final System.Logger LOGGER =
            System.getLogger(CsvGnisFeatureProvider.class.getName());
    private static final String RESOURCE_NAME = "civil_gnis_deployment_areas.csv";
    private static final List<String> EXPECTED_COLUMNS = List.of(
            "feature_id",
            "feature_name",
            "feature_class",
            "state_name",
            "prim_lat_dec",
            "prim_long_dec");

    private final Path csvPath;
    private volatile Dataset loadedDataset;

    /** Uses the Civil GNIS CSV packaged inside the timvalidator JAR. */
    public CsvGnisFeatureProvider() {
        this.csvPath = null;
    }

    /** Uses a CSV file at an explicit path, primarily for tests and custom datasets. */
    public CsvGnisFeatureProvider(Path csvPath) {
        this.csvPath = Objects.requireNonNull(csvPath, "csvPath")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public Optional<GnisFeature> findById(int gnisId) throws GnisLookupException {
        Dataset dataset = dataset();
        String invalidReason = dataset.invalidFeatures().get(gnisId);
        if (invalidReason != null) {
            throw new GnisLookupException(invalidReason);
        }
        return Optional.ofNullable(dataset.featuresById().get(gnisId));
    }

    @Override
    public List<GnisFeature> findWithin(GnisBounds bounds) throws GnisLookupException {
        Objects.requireNonNull(bounds, "bounds");
        // STRtree queries use JTS envelopes, so translate the public bounds type first
        Envelope envelope = new Envelope(
                bounds.minimumLongitude(),
                bounds.maximumLongitude(),
                bounds.minimumLatitude(),
                bounds.maximumLatitude());
        List<GnisFeature> features = new ArrayList<>();
        // Confirm exact coordinate containment instead of relying only on index candidates
        dataset().spatialIndex().query(envelope, item -> {
            GnisFeature feature = (GnisFeature) item;
            if (bounds.contains(feature.location().getX(), feature.location().getY())) {
                features.add(feature);
            }
        });
        return List.copyOf(features);
    }

    /** Returns the loaded packaged dataset or this instance's custom dataset. */
    private Dataset dataset() throws GnisLookupException {
        if (csvPath == null) {
            return PackagedDataset.get();
        }

        // Custom CSV providers cache independently because each path may contain
        // different feature data
        Dataset current = loadedDataset;
        if (current == null) {
            synchronized (this) {
                current = loadedDataset;
                if (current == null) {
                    current = load(csvPath);
                    loadedDataset = current;
                }
            }
        }
        return current;
    }

    /** Opens a custom CSV path and retains I/O failures for the validation warning. */
    private static Dataset load(Path path) throws GnisLookupException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return load(reader, path.toString());
        } catch (IOException ex) {
            throw new GnisLookupException("Unable to load the Civil GNIS CSV: " + path, ex);
        }
    }

    /** Validates the CSV and builds the in-memory ID and spatial lookup structures. */
    private static Dataset load(BufferedReader reader, String description)
            throws IOException, GnisLookupException {
        // Fail initialization when the column order changes; row parsing below relies
        // on these fixed positions
        String header = reader.readLine();
        if (header == null) {
            throw new GnisLookupException(
                    "Civil GNIS CSV has an unexpected header: " + description);
        }
        try {
            if (!parseCsvLine(stripByteOrderMark(header)).equals(EXPECTED_COLUMNS)) {
                throw new GnisLookupException(
                        "Civil GNIS CSV has an unexpected header: " + description);
            }
        } catch (IllegalArgumentException ex) {
            throw new GnisLookupException(
                    "Civil GNIS CSV has an invalid header: " + description,
                    ex);
        }

        Map<Integer, GnisFeature> featuresById = new HashMap<>();
        Map<Integer, String> invalidFeatures = new HashMap<>();
        // Build the ID lookup first while preserving malformed IDs so an exact lookup
        // can report bad source data instead of treating the ID as unknown.
        String line;
        long lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            addFeature(
                    line,
                    description,
                    lineNumber,
                    featuresById,
                    invalidFeatures);
        }

        // Index only successfully parsed representative points. Building the immutable
        // STRtree after reading also prevents partially loaded rows from entering it.
        STRtree spatialIndex = new STRtree();
        for (GnisFeature feature : featuresById.values()) {
            double longitude = feature.location().getX();
            double latitude = feature.location().getY();
            spatialIndex.insert(new Envelope(longitude, longitude, latitude, latitude), feature);
        }
        spatialIndex.build();
        return new Dataset(
                Map.copyOf(featuresById),
                Map.copyOf(invalidFeatures),
                spatialIndex);
    }

    /** Parses and validates one data row before adding it to the ID lookup. */
    private static void addFeature(
            String line,
            String description,
            long lineNumber,
            Map<Integer, GnisFeature> featuresById,
            Map<Integer, String> invalidFeatures) {
        Integer featureId = null;
        try {
            // Parse the ID before the remaining values when possible so malformed
            // records can still be associated with a later packetID lookup.
            List<String> columns = parseCsvLine(line);
            featureId = parseFeatureId(columns);
            if (columns.size() != EXPECTED_COLUMNS.size()) {
                throw new IllegalArgumentException(
                        "expected " + EXPECTED_COLUMNS.size() + " columns but found " + columns.size());
            }

            featureId = Integer.valueOf(columns.get(0));
            double latitude = Double.parseDouble(columns.get(4));
            double longitude = Double.parseDouble(columns.get(5));
            if (!Double.isFinite(latitude)
                    || !Double.isFinite(longitude)
                    || latitude < -90.0
                    || latitude > 90.0
                    || longitude < -180.0
                    || longitude > 180.0) {
                throw new IllegalArgumentException("coordinates are outside the WGS-84 range");
            }

            GnisFeature feature = new GnisFeature(
                    featureId,
                    columns.get(1),
                    columns.get(2),
                    new CoordinateXY(longitude, latitude));
            if (featuresById.putIfAbsent(featureId, feature) != null) {
                throw new IllegalArgumentException("feature_id is duplicated");
            }
        } catch (IllegalArgumentException ex) {
            // A bad row should not prevent the remaining national dataset from loading.
            String featureDescription = featureId == null
                    ? ""
                    : " for feature_id " + featureId;
            String message = String.format(
                    Locale.ROOT,
                    "Civil GNIS CSV row %d%s in %s contains invalid data: %s",
                    lineNumber,
                    featureDescription,
                    description,
                    ex.getMessage());
            if (featureId != null) {
                invalidFeatures.put(featureId, message);
                featuresById.remove(featureId);
            }
            LOGGER.log(System.Logger.Level.WARNING, message);
        }
    }

    /** Reads an ID when present without allowing a malformed ID to abort row handling. */
    private static Integer parseFeatureId(List<String> columns) {
        if (columns.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(columns.getFirst());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Parses one RFC 4180-style CSV record, including quoted commas and escaped quotes. */
    private static List<String> parseCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                // Two quotes inside a quoted value represent one literal quote.
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                columns.add(value.toString());
                value.setLength(0);
            } else {
                value.append(character);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("unterminated quoted CSV field");
        }
        columns.add(value.toString());
        return columns;
    }

    private static String stripByteOrderMark(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private record Dataset(
            Map<Integer, GnisFeature> featuresById,
            Map<Integer, String> invalidFeatures,
            STRtree spatialIndex) {
    }

    private static final class PackagedDataset {

        private static volatile Dataset dataset;

        private PackagedDataset() {
        }

        private static Dataset get() throws GnisLookupException {
            Dataset current = dataset;
            if (current == null) {
                // All default providers share one parsed map and spatial index. The
                // second check prevents duplicate loading when initialization races.
                synchronized (PackagedDataset.class) {
                    current = dataset;
                    if (current == null) {
                        current = loadPackaged();
                        dataset = current;
                    }
                }
            }
            return current;
        }

        /** Opens the classpath resource without extracting it to the filesystem. */
        private static Dataset loadPackaged() throws GnisLookupException {
            try (InputStream source = CsvGnisFeatureProvider.class
                    .getResourceAsStream(RESOURCE_NAME)) {
                if (source == null) {
                    throw new GnisLookupException(
                            "Packaged Civil GNIS CSV resource was not found: " + RESOURCE_NAME);
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        source,
                        StandardCharsets.UTF_8))) {
                    return load(reader, "packaged resource " + RESOURCE_NAME);
                }
            } catch (IOException ex) {
                throw new GnisLookupException(
                        "Unable to load the packaged Civil GNIS CSV",
                        ex);
            }
        }
    }
}
