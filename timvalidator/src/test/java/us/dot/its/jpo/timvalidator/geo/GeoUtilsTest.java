package us.dot.its.jpo.timvalidator.geo;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import us.dot.its.jpo.timvalidator.road.GeoUtils;

class GeoUtilsTest {


    @Test
    void coordinateIsValid_returnsTrueForValidCoordinate() throws Exception {
        Coordinate testCoordinate = new Coordinate(-104.8757684, 39.7619791);
        assertTrue(GeoUtils.coordinateIsValid(testCoordinate));
    }

    @Test
    void coordinateIsValid_returnsFalseForInvalidCoordinate() throws Exception {
        Coordinate testCoordinate = new Coordinate(-200.0, 100.0);
        assertTrue(!GeoUtils.coordinateIsValid(testCoordinate));
    }

    @Test
    void coordinateIsValid_returnsFalseForPartiallyInvalidCoordinate() throws Exception {
        Coordinate testCoordinate = new Coordinate(0, 100.0);
        assertTrue(!GeoUtils.coordinateIsValid(testCoordinate));
    }

    @Test
    void coordinateIsValid_returnsFalseForNullCoordinate() throws Exception {
        Coordinate testCoordinate = null;
        assertTrue(!GeoUtils.coordinateIsValid(testCoordinate));
    }

    @Test
    void coordinateIsValid_returnsFalseForInfiniteCoordinate() throws Exception {
        Coordinate testCoordinate = new Coordinate(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        assertTrue(!GeoUtils.coordinateIsValid(testCoordinate));
    }    
}
