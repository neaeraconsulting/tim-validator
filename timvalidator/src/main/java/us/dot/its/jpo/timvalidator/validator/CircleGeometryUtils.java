package us.dot.its.jpo.timvalidator.validator;

import java.util.OptionalDouble;

import org.locationtech.proj4j.units.Units;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.Circle;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.DistanceUnits;

/** Shared conversions for J2735 circle geometry. */
final class CircleGeometryUtils {

    private CircleGeometryUtils() {
    }

    static OptionalDouble radiusMeters(Circle circle) {
        if (circle == null || circle.getRadius() == null || circle.getUnits() == null) {
            return OptionalDouble.empty();
        }

        double encodedRadius = circle.getRadius().getValue();
        DistanceUnits units = circle.getUnits();
        double radiusMeters = switch (units) {
            case CENTIMETER -> Units.convert(
                    encodedRadius,
                    Units.CENTIMETRES,
                    Units.METRES);
            case CM2_5 -> Units.convert(
                    encodedRadius * 2.5,
                    Units.CENTIMETRES,
                    Units.METRES);
            case DECIMETER -> Units.convert(
                    encodedRadius,
                    Units.DECIMETRES,
                    Units.METRES);
            case METER -> encodedRadius;
            case KILOMETER -> Units.convert(
                    encodedRadius,
                    Units.KILOMETRES,
                    Units.METRES);
            case FOOT -> Units.convert(
                    encodedRadius,
                    Units.FEET,
                    Units.METRES);
            case YARD -> Units.convert(
                    encodedRadius,
                    Units.YARDS,
                    Units.METRES);
            case MILE -> Units.convert(
                    encodedRadius,
                    Units.MILES,
                    Units.METRES);
        };
        return Double.isFinite(radiusMeters) && radiusMeters > 0.0
                ? OptionalDouble.of(radiusMeters)
                : OptionalDouble.empty();
    }
}
