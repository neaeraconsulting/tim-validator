package us.dot.its.jpo.timvalidator.road;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import us.dot.its.jpo.timvalidator.exception.InvalidGeometryException;

class GeoUtilsTest {


    @Test
    void coordinateIsValid_returnsTrueForValidCoordinate() throws Exception {
        Coordinate testCoordinate = new Coordinate(-104.8757684, 39.7619791);
        assertTrue(GeoUtils.coordinateIsValid(testCoordinate));
    }

    @Test
    void coordinateIsValid_returnsFalseForInvalidCoordinate() throws Exception {
        Coordinate testCoordinate = new Coordinate(-200.0, 100.0);
        assertFalse(GeoUtils.coordinateIsValid(testCoordinate));
    }

    @Test
    void coordinateIsValid_returnsFalseForPartiallyInvalidCoordinate() throws Exception {
        Coordinate testCoordinate = new Coordinate(0, 100.0);
        assertFalse(GeoUtils.coordinateIsValid(testCoordinate));
    }

    @Test
    void coordinateIsValid_returnsFalseForNullCoordinate() throws Exception {
        Coordinate testCoordinate = null;
        assertFalse(GeoUtils.coordinateIsValid(testCoordinate));
    }

    @Test
    void coordinateIsValid_returnsFalseForInfiniteCoordinate() throws Exception {
        Coordinate testCoordinate = new Coordinate(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        assertFalse(GeoUtils.coordinateIsValid(testCoordinate));
    }

    @Test
    void validateCoordinate_acceptsValidCoordinate() {
        Coordinate testCoordinate = new Coordinate(-104.8757684, 39.7619791);
        assertDoesNotThrow(() -> GeoUtils.validateCoordinate(testCoordinate));
    }

    @Test
    void validateCoordinate_throwsForLatitudeOutOfRange() {
        Coordinate testCoordinate = new Coordinate(0.0, 91.0);
        assertThrows(InvalidGeometryException.class, () -> GeoUtils.validateCoordinate(testCoordinate));
    }

    @Test
    void validateCoordinate_throwsForLongitudeOutOfRange() {
        Coordinate testCoordinate = new Coordinate(181.0, 0.0);
        assertThrows(InvalidGeometryException.class, () -> GeoUtils.validateCoordinate(testCoordinate));
    }

    @Test
    void validateCoordinate_throwsForNullCoordinate() {
        assertThrows(NullPointerException.class, () -> GeoUtils.validateCoordinate(null));
    }

    @Test
    void validateCoordinate_throwsForInfiniteCoordinate() {
        Coordinate testCoordinate = new Coordinate(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        assertThrows(InvalidGeometryException.class, () -> GeoUtils.validateCoordinate(testCoordinate));
    }
}
