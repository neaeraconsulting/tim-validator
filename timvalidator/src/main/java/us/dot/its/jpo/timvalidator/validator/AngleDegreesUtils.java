package us.dot.its.jpo.timvalidator.validator;

import org.locationtech.jts.algorithm.Angle;

/**
 * Degree-domain wrappers around JTS's radian-based Angle utilities.
 */
final class AngleDegreesUtils {
  private AngleDegreesUtils() {}

  static double normalizeDegrees(double degrees) {
    return Math.toDegrees(Angle.normalizePositive(Math.toRadians(degrees)));
  }

  static double diffDegrees(double degrees1, double degrees2) {
    return Math.toDegrees(Angle.diff(
        Math.toRadians(degrees1),
        Math.toRadians(degrees2)));
  }
}
