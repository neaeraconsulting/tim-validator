package us.dot.its.jpo.timvalidator.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import com.fasterxml.jackson.databind.ObjectMapper;

class OverpassRoadGeometryProviderTest {

    @Test
    void findNearbyRoads_buildsQueryAndDecodesWayGeometry() {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        AtomicReference<Duration> capturedTimeout = new AtomicReference<>();
        AtomicReference<String> capturedUserAgent = new AtomicReference<>();
        OverpassRoadGeometryProvider provider = provider((endpoint, query, timeout, userAgent) -> {
            assertEquals(URI.create("https://overpass.example/api/interpreter"), endpoint);
            capturedQuery.set(query);
            capturedTimeout.set(timeout);
            capturedUserAgent.set(userAgent);
            return """
                    {
                      "elements": [
                        {"type":"node","id":1,"lat":40.0,"lon":-105.0},
                        {
                          "type":"way",
                          "id":123,
                          "tags":{"highway":"primary","name":"Main Street"},
                          "geometry":[
                            {"lat":39.999,"lon":-105.0},
                            {"lat":40.001,"lon":-105.0}
                          ]
                        },
                        {
                          "type":"way",
                          "id":456,
                          "tags":{"highway":"service"},
                          "geometry":[{"lat":40.0,"lon":-105.0}]
                        }
                      ]
                    }
                    """;
        });

        List<RoadSegment> roads =
                provider.findNearbyRoads(new Coordinate(-105.0, 40.0), 30.0);

        assertEquals(1, roads.size());
        RoadSegment road = roads.getFirst();
        assertEquals(123L, road.sourceId());
        assertEquals("Main Street", road.name());
        assertEquals(2, road.geometry().getNumPoints());
        assertEquals(39.999, road.geometry().getCoordinateN(0).getY());
        assertTrue(capturedQuery.get().contains("way(around:30.00,40.0000000,-105.0000000)"));
        assertTrue(capturedQuery.get().contains("[timeout:3]"));
        assertTrue(capturedQuery.get().contains("[\"highway\"~"));
        assertFalse(capturedQuery.get().contains("|track"));
        assertFalse(capturedQuery.get().contains("|construction"));
        assertTrue(capturedQuery.get().contains("[\"access\"!~\"^(no|private)$\"]"));
        assertTrue(capturedQuery.get().contains("[\"vehicle\"!~\"^(no|private)$\"]"));
        assertTrue(capturedQuery.get().contains("[\"motor_vehicle\"!~\"^(no|private)$\"]"));
        assertTrue(capturedQuery.get().contains("[\"area\"!~\"^yes$\"]"));
        assertTrue(capturedQuery.get().contains(
                "[\"service\"!~\"^(driveway|parking_aisle)$\"]"));
        assertTrue(capturedQuery.get().contains("out tags geom"));
        assertEquals(Duration.ofSeconds(7), capturedTimeout.get());
        assertEquals("test-validator/1.0", capturedUserAgent.get());
    }

    @Test
    void findNearbyRoads_usesRoadReferenceWhenNameIsMissing() {
        OverpassRoadGeometryProvider provider = provider((endpoint, query, timeout, userAgent) -> """
                {
                  "elements": [{
                    "type":"way",
                    "id":789,
                    "tags":{"highway":"trunk","ref":"US 36"},
                    "geometry":[
                      {"lat":40.0,"lon":-105.001},
                      {"lat":40.0,"lon":-104.999}
                    ]
                  }]
                }
                """);

        List<RoadSegment> roads =
                provider.findNearbyRoads(new Coordinate(-105.0, 40.0), 30.0);

        assertEquals("US 36", roads.getFirst().name());
    }

    @Test
    void findNearbyRoads_skipsMalformedNumbersAndRetainsNumericZeroCoordinates() {
        OverpassRoadGeometryProvider provider = provider((endpoint, query, timeout, userAgent) -> """
                {
                  "elements": [
                    {
                      "type":"way",
                      "id":100,
                      "geometry":[
                        {"lat":0,"lon":0},
                        {"lat":0.001,"lon":0}
                      ]
                    },
                    {
                      "type":"way",
                      "id":"101",
                      "geometry":[
                        {"lat":0,"lon":0},
                        {"lat":0.001,"lon":0}
                      ]
                    },
                    {
                      "type":"way",
                      "id":102,
                      "geometry":[
                        {"lat":"unknown","lon":0},
                        {"lat":0.001,"lon":0}
                      ]
                    },
                    {
                      "type":"way",
                      "id":103,
                      "geometry":[
                        {"lat":null,"lon":0},
                        {"lat":0.001,"lon":0}
                      ]
                    },
                    {
                      "type":"way",
                      "id":0,
                      "geometry":[
                        {"lat":0,"lon":0},
                        {"lat":0.001,"lon":0}
                      ]
                    }
                  ]
                }
                """);

        List<RoadSegment> roads =
                provider.findNearbyRoads(new Coordinate(0.0, 0.0), 30.0);

        assertEquals(1, roads.size());
        assertEquals(100L, roads.getFirst().sourceId());
        assertEquals(0.0, roads.getFirst().geometry().getCoordinateN(0).getX());
        assertEquals(0.0, roads.getFirst().geometry().getCoordinateN(0).getY());
    }

    @Test
    void findNearbyRoads_rejectsMalformedOverpassResponse() {
        OverpassRoadGeometryProvider provider =
                provider((endpoint, query, timeout, userAgent) -> "{\"remark\":\"error\"}");

        RoadGeometryLookupException exception = assertThrows(
                RoadGeometryLookupException.class,
                () -> provider.findNearbyRoads(new Coordinate(-105.0, 40.0), 30.0));

        assertTrue(exception.getMessage().contains("elements array"));
    }

    private static OverpassRoadGeometryProvider provider(
            OverpassRoadGeometryProvider.OverpassTransport transport) {
        return new OverpassRoadGeometryProvider(
                URI.create("https://overpass.example/api/interpreter"),
                Duration.ofMillis(2_001),
                Duration.ofSeconds(7),
                "test-validator/1.0",
                new ObjectMapper(),
                transport);
    }
}
