package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Circle;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.DistanceUnits;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Radius_B12;

class CircleGeometryUtilsTest {

    @ParameterizedTest
    @MethodSource("unitConversions")
    void radiusMeters_convertsEveryJ2735Unit(
            DistanceUnits units,
            double expectedMeters) {
        Circle circle = new Circle();
        circle.setRadius(new Radius_B12(2));
        circle.setUnits(units);

        assertEquals(
                expectedMeters,
                CircleGeometryUtils.radiusMeters(circle).orElseThrow(),
                1.0e-9);
    }

    @Test
    void radiusMeters_rejectsIncompleteOrNonPositiveCircles() {
        assertTrue(CircleGeometryUtils.radiusMeters(null).isEmpty());
        assertTrue(CircleGeometryUtils.radiusMeters(new Circle()).isEmpty());

        Circle missingUnits = new Circle();
        missingUnits.setRadius(new Radius_B12(2));
        assertTrue(CircleGeometryUtils.radiusMeters(missingUnits).isEmpty());

        Circle zeroRadius = new Circle();
        zeroRadius.setRadius(new Radius_B12(0));
        zeroRadius.setUnits(DistanceUnits.METER);
        assertTrue(CircleGeometryUtils.radiusMeters(zeroRadius).isEmpty());
    }

    private static Stream<Arguments> unitConversions() {
        return Stream.of(
                Arguments.of(DistanceUnits.CENTIMETER, 0.02),
                Arguments.of(DistanceUnits.CM2_5, 0.05),
                Arguments.of(DistanceUnits.DECIMETER, 0.2),
                Arguments.of(DistanceUnits.METER, 2.0),
                Arguments.of(DistanceUnits.KILOMETER, 2_000.0),
                Arguments.of(DistanceUnits.FOOT, 0.6096),
                Arguments.of(DistanceUnits.YARD, 1.8288),
                Arguments.of(DistanceUnits.MILE, 3_218.688));
    }
}
