package us.dot.its.jpo.timvalidator.road;

import static us.dot.its.jpo.timvalidator.road.GeoUtils.coordinateIsValid;
import static us.dot.its.jpo.timvalidator.road.GeoUtils.validateCoordinate;

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
import java.util.StringJoiner;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import us.dot.its.jpo.timvalidator.exception.InvalidGeometryException;
import us.dot.its.jpo.timvalidator.exception.RoadGeometryLookupException;
import us.dot.its.jpo.timvalidator.exception.ValidationException;

/**
 * Retrieves nearby motor-vehicle roadways from an OpenStreetMap Overpass endpoint.
 *
 * <p>This class has no default endpoint or User-Agent: callers must supply both. The Overpass
 * API's public instances are shared, rate-limited infrastructure; see the usage guidelines at
 * <a href="https://wiki.openstreetmap.org/wiki/Overpass_API#Rules_of_usage">
 * wiki.openstreetmap.org/wiki/Overpass_API#Rules_of_usage</a> before choosing an endpoint and a
 * User-Agent that uniquely identifies your application.
 */
public final class OverpassRoadGeometryProvider implements RoadGeometryProvider {

    private static final Duration DEFAULT_QUERY_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final List<String> ALLOWED_ROAD_CLASSES = List.of(
            "motorway",
            "motorway_link",
            "trunk",
            "trunk_link",
            "primary",
            "primary_link",
            "secondary",
            "secondary_link",
            "tertiary",
            "tertiary_link",
            "unclassified",
            "residential",
            "living_street");
    private static final String ALLOWED_ROAD_CLASS_PATTERN =
            String.join("|", ALLOWED_ROAD_CLASSES);
    private static final List<String> ACCESS_TAGS =
            List.of("access", "vehicle", "motor_vehicle");
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private final URI endpoint;
    private final Duration queryTimeout;
    private final Duration httpTimeout;
    private final String userAgent;
    private final ObjectMapper objectMapper;
    private final OverpassTransport transport;

    /**
     * Creates a provider using the library's default timeouts, against a caller-chosen
     * Overpass endpoint and User-Agent.
     *
     * @param endpoint the Overpass API endpoint to query
     * @param userAgent a value that uniquely identifies the calling application, per the
     *        Overpass usage guidelines
     */
    public OverpassRoadGeometryProvider(String endpoint, String userAgent) {
        this(endpoint, DEFAULT_QUERY_TIMEOUT, DEFAULT_HTTP_TIMEOUT, userAgent);
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
    public List<RoadSegment> findNearbyRoads(Coordinate location, double radiusMeters)
        throws ValidationException {
        validateCoordinate(location);
        if (!Double.isFinite(radiusMeters) || radiusMeters <= 0.0) {
            throw new IllegalArgumentException("radiusMeters must be positive");
        }

        String query = buildQuery(location.getY(), location.getX(), radiusMeters);
        return executeQuery(query);
    }

    @Override
    public List<RoadSegment> findRoadsIn(Polygon searchArea) throws ValidationException {
        validateSearchArea(searchArea);
        return executeQuery(buildPolygonQuery(searchArea));
    }

    private List<RoadSegment> executeQuery(String query) throws ValidationException {
        String response = transport.execute(endpoint, query, httpTimeout, userAgent);
        return parseRoads(response);
    }

    String buildQuery(double latitudeDegrees, double longitudeDegrees, double radiusMeters) {
        return buildRoadQuery(String.format(
                Locale.ROOT,
                "(around:%.2f,%.7f,%.7f)",
                radiusMeters,
                latitudeDegrees,
                longitudeDegrees));
    }

    String buildPolygonQuery(Polygon searchArea) throws InvalidGeometryException {
        validateSearchArea(searchArea);
        Coordinate[] coordinates = searchArea.getExteriorRing().getCoordinates();
        StringJoiner polygonCoordinates = new StringJoiner(" ");
        // Overpass closes the polygon, so omit the duplicate JTS ring endpoint.
        for (int index = 0; index < coordinates.length - 1; index++) {
            Coordinate coordinate = coordinates[index];
            polygonCoordinates.add(String.format(
                    Locale.ROOT,
                    "%.7f %.7f",
                    coordinate.getY(),
                    coordinate.getX()));
        }
        return buildRoadQuery("(poly:\"" + polygonCoordinates + "\")");
    }

    private String buildRoadQuery(String spatialFilter) {
        long timeoutSeconds = Math.max(1L, (queryTimeout.toMillis() + 999L) / 1_000L);
        return String.format(
                Locale.ROOT,
                "[out:json][timeout:%1$d];"
                        + "("
                        + "way%2$s"
                        + "[\"highway\"~\"^(%3$s)$\"]"
                        + "[\"access\"!~\"^(no|private)$\"]"
                        + "[\"vehicle\"!~\"^(no|private)$\"]"
                        + "[\"motor_vehicle\"!~\"^(no|private)$\"]"
                        + "[\"area\"!~\"^yes$\"];"
                        + "way%2$s"
                        + "[\"highway\"=\"construction\"]"
                        + "[\"construction\"~\"^(%3$s)$\"]"
                        + "[\"access\"!~\"^private$\"]"
                        + "[\"vehicle\"!~\"^private$\"]"
                        + "[\"motor_vehicle\"!~\"^private$\"]"
                        + "[\"area\"!~\"^yes$\"];"
                        + ");"
                        + "out tags geom;",
                timeoutSeconds,
                spatialFilter,
                ALLOWED_ROAD_CLASS_PATTERN);
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private List<RoadSegment> parseRoads(String response) throws ValidationException {
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
                if (!isEligibleRoad(tags)) {
                    continue;
                }
                String name = tags.path("name").asText(null);
                if (name == null || name.isBlank()) {
                    name = tags.path("ref").asText(null);
                }
                try {
                    roads.add(RoadSegment.validRoadSegment(idNode.longValue(), name, geometry));
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
            if (!coordinateIsValid(coordinate)) {
                return null;
            }
            geometry.add(coordinate);
        }
        return geometry.size() < 2
                ? null
                : GEOMETRY_FACTORY.createLineString(geometry.toArray(Coordinate[]::new));
    }

    private boolean isEligibleRoad(JsonNode tags) {
        if (!tags.isObject() || "yes".equals(tags.path("area").asText())) {
            return false;
        }

        String highway = tags.path("highway").asText();
        if (ALLOWED_ROAD_CLASSES.contains(highway)) {
            return !hasRestrictedAccess(tags, true);
        }
        return "construction".equals(highway)
                && ALLOWED_ROAD_CLASSES.contains(tags.path("construction").asText())
                && !hasRestrictedAccess(tags, false);
    }

    private boolean hasRestrictedAccess(JsonNode tags, boolean rejectNoAccess) {
        for (String accessTag : ACCESS_TAGS) {
            String value = tags.path(accessTag).asText();
            if ("private".equals(value) || (rejectNoAccess && "no".equals(value))) {
                return true;
            }
        }
        return false;
    }

    private void validateSearchArea(Polygon searchArea) throws InvalidGeometryException {
        Objects.requireNonNull(searchArea, "searchArea");
        if (searchArea.isEmpty()
                || !searchArea.isValid()
                || searchArea.getNumInteriorRing() != 0) {
            throw new IllegalArgumentException(
                    "searchArea must be a valid, non-empty polygon without holes");
        }
        for (Coordinate coordinate : searchArea.getCoordinates()) {
            validateCoordinate(coordinate);
        }
    }

    @FunctionalInterface
    interface OverpassTransport {
        String execute(URI endpoint, String query, Duration timeout, String userAgent)
            throws RoadGeometryLookupException;
    }

    private static final class JdkOverpassTransport implements OverpassTransport{

        private final HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        @Override
        public String execute(URI endpoint, String query, Duration timeout, String userAgent)
                throws RoadGeometryLookupException {
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
                throw new RoadGeometryLookupException("Overpass request was interrupted", ex);
            } catch (IOException ex) {
                throw new RoadGeometryLookupException("Overpass request failed", ex);
            }
        }
    }
}
