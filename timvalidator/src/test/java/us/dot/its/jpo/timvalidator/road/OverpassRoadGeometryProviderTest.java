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
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import com.fasterxml.jackson.databind.ObjectMapper;

class OverpassRoadGeometryProviderTest {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

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
                          "geometry":[
                            {"lat":40.0,"lon":-105.001},
                            {"lat":40.0,"lon":-104.999}
                          ]
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
        assertFalse(capturedQuery.get().contains("|service"));
        assertFalse(capturedQuery.get().contains("|road"));
        assertFalse(capturedQuery.get().contains("|track"));
        assertFalse(capturedQuery.get().contains("|cycleway"));
        assertFalse(capturedQuery.get().contains("|pedestrian"));
        assertTrue(capturedQuery.get().contains("[\"access\"!~\"^(no|private)$\"]"));
        assertTrue(capturedQuery.get().contains("[\"vehicle\"!~\"^(no|private)$\"]"));
        assertTrue(capturedQuery.get().contains("[\"motor_vehicle\"!~\"^(no|private)$\"]"));
        assertTrue(capturedQuery.get().contains("[\"area\"!~\"^yes$\"]"));
        assertFalse(capturedQuery.get().contains("[\"service\"!~"));
        assertTrue(capturedQuery.get().contains("out tags geom"));
        assertEquals(Duration.ofSeconds(7), capturedTimeout.get());
        assertEquals("test-validator/1.0", capturedUserAgent.get());
    }

    @Test
    void findNearbyRoads_includesConstructionOnlyForAllowedRoadClass() {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        OverpassRoadGeometryProvider provider = provider((endpoint, query, timeout, userAgent) -> {
            capturedQuery.set(query);
            return "{\"elements\":[]}";
        });

        provider.findNearbyRoads(new Coordinate(-105.0, 40.0), 30.0);

        String query = capturedQuery.get();
        assertTrue(query.contains(
                "[\"highway\"=\"construction\"]"
                        + "[\"construction\"~\"^(motorway|motorway_link|trunk|trunk_link|"
                        + "primary|primary_link|secondary|secondary_link|tertiary|"
                        + "tertiary_link|unclassified|residential|living_street)$\"]"));
        assertTrue(query.contains(
                "[\"construction\"~"
                        + "\"^(motorway|motorway_link|trunk|trunk_link|primary|primary_link|"
                        + "secondary|secondary_link|tertiary|tertiary_link|unclassified|"
                        + "residential|living_street)$\"]"
                        + "[\"access\"!~\"^private$\"]"
                        + "[\"vehicle\"!~\"^private$\"]"
                        + "[\"motor_vehicle\"!~\"^private$\"]"
                        + "[\"area\"!~\"^yes$\"]"));
    }

    @Test
    void findNearbyRoads_filtersReturnedWaysUsingTheSameRoadPolicy() {
        OverpassRoadGeometryProvider provider = provider((endpoint, query, timeout, userAgent) -> """
                {
                  "elements": [
                    {
                      "type":"way",
                      "id":1,
                      "tags":{"highway":"residential"},
                      "geometry":[
                        {"lat":40.0,"lon":-105.001},
                        {"lat":40.0,"lon":-104.999}
                      ]
                    },
                    {
                      "type":"way",
                      "id":2,
                      "tags":{
                        "highway":"construction",
                        "construction":"primary",
                        "access":"no",
                        "vehicle":"no",
                        "motor_vehicle":"no"
                      },
                      "geometry":[
                        {"lat":40.001,"lon":-105.0},
                        {"lat":39.999,"lon":-105.0}
                      ]
                    },
                    {
                      "type":"way",
                      "id":3,
                      "tags":{"highway":"service"},
                      "geometry":[
                        {"lat":40.0,"lon":-105.001},
                        {"lat":40.0,"lon":-104.999}
                      ]
                    },
                    {
                      "type":"way",
                      "id":4,
                      "tags":{"highway":"road"},
                      "geometry":[
                        {"lat":40.0,"lon":-105.001},
                        {"lat":40.0,"lon":-104.999}
                      ]
                    },
                    {
                      "type":"way",
                      "id":5,
                      "tags":{"highway":"cycleway"},
                      "geometry":[
                        {"lat":40.0,"lon":-105.001},
                        {"lat":40.0,"lon":-104.999}
                      ]
                    },
                    {
                      "type":"way",
                      "id":6,
                      "tags":{"highway":"construction","construction":"service"},
                      "geometry":[
                        {"lat":40.0,"lon":-105.001},
                        {"lat":40.0,"lon":-104.999}
                      ]
                    },
                    {
                      "type":"way",
                      "id":7,
                      "tags":{"highway":"construction","construction":"primary","access":"private"},
                      "geometry":[
                        {"lat":40.0,"lon":-105.001},
                        {"lat":40.0,"lon":-104.999}
                      ]
                    },
                    {
                      "type":"way",
                      "id":8,
                      "tags":{"highway":"primary","access":"no"},
                      "geometry":[
                        {"lat":40.0,"lon":-105.001},
                        {"lat":40.0,"lon":-104.999}
                      ]
                    },
                    {
                      "type":"way",
                      "id":9,
                      "tags":{"highway":"primary","area":"yes"},
                      "geometry":[
                        {"lat":40.0,"lon":-105.001},
                        {"lat":40.0,"lon":-104.999}
                      ]
                    }
                  ]
                }
                """);

        List<RoadSegment> roads =
                provider.findNearbyRoads(new Coordinate(-105.0, 40.0), 30.0);

        assertEquals(List.of(1L, 2L), roads.stream().map(RoadSegment::sourceId).toList());
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
    void findRoadsIn_buildsPolygonQueryWithLatitudeLongitudeOrder() {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        OverpassRoadGeometryProvider provider = provider((endpoint, query, timeout, userAgent) -> {
            capturedQuery.set(query);
            return "{\"elements\":[]}";
        });
        Polygon searchArea = GEOMETRY_FACTORY.createPolygon(new Coordinate[] {
            new Coordinate(-105.001, 39.999),
            new Coordinate(-104.999, 39.999),
            new Coordinate(-104.999, 40.001),
            new Coordinate(-105.001, 40.001),
            new Coordinate(-105.001, 39.999)
        });

        List<RoadSegment> roads = provider.findRoadsIn(searchArea);

        assertTrue(roads.isEmpty());
        assertTrue(capturedQuery.get().contains(
                "way(poly:\"39.9990000 -105.0010000 "
                        + "39.9990000 -104.9990000 "
                        + "40.0010000 -104.9990000 "
                        + "40.0010000 -105.0010000\")"));
        assertTrue(capturedQuery.get().contains("[\"highway\"~"));
        assertTrue(capturedQuery.get().contains("out tags geom"));
    }

    @Test
    void findNearbyRoads_skipsMalformedNumbersAndRetainsNumericZeroCoordinates() {
        OverpassRoadGeometryProvider provider = provider((endpoint, query, timeout, userAgent) -> """
                {
                  "elements": [
                    {
                      "type":"way",
                      "id":100,
                      "tags":{"highway":"residential"},
                      "geometry":[
                        {"lat":0,"lon":0},
                        {"lat":0.001,"lon":0}
                      ]
                    },
                    {
                      "type":"way",
                      "id":"101",
                      "tags":{"highway":"residential"},
                      "geometry":[
                        {"lat":0,"lon":0},
                        {"lat":0.001,"lon":0}
                      ]
                    },
                    {
                      "type":"way",
                      "id":102,
                      "tags":{"highway":"residential"},
                      "geometry":[
                        {"lat":"unknown","lon":0},
                        {"lat":0.001,"lon":0}
                      ]
                    },
                    {
                      "type":"way",
                      "id":103,
                      "tags":{"highway":"residential"},
                      "geometry":[
                        {"lat":null,"lon":0},
                        {"lat":0.001,"lon":0}
                      ]
                    },
                    {
                      "type":"way",
                      "id":0,
                      "tags":{"highway":"residential"},
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
