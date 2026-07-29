package us.dot.its.jpo.timvalidator.road;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Retrieves nearby motor-vehicle road ways from an OpenStreetMap Overpass endpoint.
 */
public final class OverpassRoadGeometryProvider implements RoadGeometryProvider {

    private static final String DEFAULT_ENDPOINT =
            "https://overpass-api.de/api/interpreter";
    private static final Duration DEFAULT_QUERY_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final String DEFAULT_USER_AGENT = "timvalidator/1.0";
    private static final String ROAD_HIGHWAY_VALUES =
            "motorway|motorway_link|trunk|trunk_link|primary|primary_link|"
                    + "secondary|secondary_link|tertiary|tertiary_link|unclassified|"
                    + "residential|living_street|service|road";
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private final URI endpoint;
    private final Duration queryTimeout;
    private final Duration httpTimeout;
    private final String userAgent;
    private final ObjectMapper objectMapper;
    private final OverpassTransport transport;

    /**
     * Creates a provider using the validator library's standard Overpass settings.
     */
    public OverpassRoadGeometryProvider() {
        this(
                DEFAULT_ENDPOINT,
                DEFAULT_QUERY_TIMEOUT,
                DEFAULT_HTTP_TIMEOUT,
                DEFAULT_USER_AGENT);
    }

    public OverpassRoadGeometryProvider(String endpoint, Duration timeout, String userAgent) {
        this(endpoint, timeout, timeout, userAgent);
    }

    public OverpassRoadGeometryProvider(
            String endpoint,
            Duration queryTimeout,
            Duration httpTimeout,
            String userAgent) {
        this(
                URI.create(Objects.requireNonNull(endpoint, "endpoint")),
                queryTimeout,
                httpTimeout,
                userAgent,
                new ObjectMapper(),
                new JdkOverpassTransport());
    }

    OverpassRoadGeometryProvider(
            URI endpoint,
            Duration queryTimeout,
            Duration httpTimeout,
            String userAgent,
            ObjectMapper objectMapper,
            OverpassTransport transport) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.queryTimeout = requirePositive(queryTimeout, "queryTimeout");
        this.httpTimeout = requirePositive(httpTimeout, "httpTimeout");
        this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.transport = Objects.requireNonNull(transport, "transport");

        if (userAgent.isBlank()) {
            throw new IllegalArgumentException("userAgent must not be blank");
        }
    }

    @Override
    public List<RoadSegment> findNearbyRoads(Coordinate location, double radiusMeters) {
        validateLocation(location);
        if (!Double.isFinite(radiusMeters) || radiusMeters <= 0.0) {
            throw new IllegalArgumentException("radiusMeters must be positive");
        }

        String query = buildQuery(location.getY(), location.getX(), radiusMeters);
        String response = transport.execute(endpoint, query, httpTimeout, userAgent);
        return parseRoads(response);
    }

    String buildQuery(double latitudeDegrees, double longitudeDegrees, double radiusMeters) {
        long timeoutSeconds = Math.max(1L, (queryTimeout.toMillis() + 999L) / 1_000L);
        return String.format(
                Locale.ROOT,
                "[out:json][timeout:%d];"
                        + "way(around:%.2f,%.7f,%.7f)"
                        + "[\"highway\"~\"^(%s)$\"]"
                        + "[\"access\"!~\"^(no|private)$\"]"
                        + "[\"vehicle\"!~\"^(no|private)$\"]"
                        + "[\"motor_vehicle\"!~\"^(no|private)$\"]"
                        + "[\"area\"!~\"^yes$\"]"
                        + "[\"service\"!~\"^(driveway|parking_aisle)$\"];"
                        + "out tags geom;",
                timeoutSeconds,
                radiusMeters,
                latitudeDegrees,
                longitudeDegrees,
                ROAD_HIGHWAY_VALUES);
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private List<RoadSegment> parseRoads(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            if (root == null) {
                throw new RoadGeometryLookupException("Overpass response is empty");
            }
            JsonNode elements = root.path("elements");
            if (!elements.isArray()) {
                throw new RoadGeometryLookupException("Overpass response does not contain an elements array");
            }

            List<RoadSegment> roads = new ArrayList<>();
            for (JsonNode element : elements) {
                if (!"way".equals(element.path("type").asText())) {
                    continue;
                }

                JsonNode idNode = element.get("id");
                if (idNode == null
                        || !idNode.isIntegralNumber()
                        || !idNode.canConvertToLong()
                        || idNode.longValue() <= 0L) {
                    continue;
                }

                LineString geometry = parseGeometry(element.path("geometry"));
                if (geometry == null) {
                    continue;
                }

                JsonNode tags = element.path("tags");
                String name = tags.path("name").asText(null);
                if (name == null || name.isBlank()) {
                    name = tags.path("ref").asText(null);
                }
                try {
                    roads.add(new RoadSegment(idNode.longValue(), name, geometry));
                } catch (IllegalArgumentException ex) {
                    // Ignore malformed individual ways while retaining other valid candidates.
                }
            }
            return List.copyOf(roads);
        } catch (RoadGeometryLookupException ex) {
            throw ex;
        } catch (IOException | IllegalArgumentException ex) {
            throw new RoadGeometryLookupException("Unable to decode Overpass road geometry", ex);
        }
    }

    private LineString parseGeometry(JsonNode geometryNode) {
        if (!geometryNode.isArray()) {
            return null;
        }

        List<Coordinate> geometry = new ArrayList<>();
        for (JsonNode point : geometryNode) {
            JsonNode latitudeNode = point.get("lat");
            JsonNode longitudeNode = point.get("lon");
            if (latitudeNode == null
                    || longitudeNode == null
                    || !latitudeNode.isNumber()
                    || !longitudeNode.isNumber()) {
                return null;
            }

            Coordinate coordinate = new Coordinate(
                    longitudeNode.doubleValue(),
                    latitudeNode.doubleValue());
            if (!coordinate.isValid()
                    || coordinate.getY() < -90.0
                    || coordinate.getY() > 90.0
                    || coordinate.getX() < -180.0
                    || coordinate.getX() > 180.0) {
                return null;
            }
            geometry.add(coordinate);
        }
        return geometry.size() < 2
                ? null
                : GEOMETRY_FACTORY.createLineString(geometry.toArray(Coordinate[]::new));
    }

    private void validateLocation(Coordinate location) {
        Objects.requireNonNull(location, "location");
        if (!location.isValid()
                || location.getY() < -90.0
                || location.getY() > 90.0
                || location.getX() < -180.0
                || location.getX() > 180.0) {
            throw new IllegalArgumentException("Invalid WGS-84 coordinate");
        }
    }

    @FunctionalInterface
    interface OverpassTransport {
        String execute(URI endpoint, String query, Duration timeout, String userAgent);
    }

    private static final class JdkOverpassTransport implements OverpassTransport {

        private final HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        @Override
        public String execute(URI endpoint, String query, Duration timeout, String userAgent) {
            String requestBody = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            try {
                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new RoadGeometryLookupException(
                            "Overpass request failed with HTTP " + response.statusCode());
                }
                return response.body();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RoadGeometryLookupException("Overpass request was interrupted", ex);
            } catch (IOException ex) {
                throw new RoadGeometryLookupException("Overpass request failed", ex);
            }
        }
    }
}
