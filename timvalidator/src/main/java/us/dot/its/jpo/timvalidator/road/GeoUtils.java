package us.dot.its.jpo.timvalidator.road;

import java.util.Objects;
import org.locationtech.jts.geom.Coordinate;
import us.dot.its.jpo.timvalidator.exception.InvalidGeometryException;

/**
 * Utility class for basic geographic coordinate validation.
 */
public final class GeoUtils {



  /**
   * Private constructor to prevent instantiation of this utility class. All references to this class should be done statically
   */
  private GeoUtils() {
  }

  /**
   * Checks if the given coordinate is valid according to WGS-84 standards. A coordinate is considered valid if it resides between -90 and 90 degrees latitude and -180 and 180 degrees longitude.
   * Coordinates are aligned such that the X coordinate of the {@code Coordinate} object represents longitude and the Y coordinate represents latitude.
   * @param coordinate the coordinate to check
   * @return true if the coordinate is valid according to WGS-84 standards, the function will return false if the coordinate is null or invalid.
   */
  public static boolean coordinateIsValid(Coordinate coordinate) {
    return (coordinate != null
        && coordinate.isValid()
        && coordinate.getY() >= -90.0
        && coordinate.getY() <= 90.0
        && coordinate.getX() >= -180.0
        && coordinate.getX() <= 180.0);
  }

  /**
   * Validates the given coordinate according to WGS-84 standards. Throws an exception if the coordinate is invalid.
   *
   * @param coordinate the coordinate to validate
   * @throws InvalidGeometryException if the coordinate is not valid according to WGS-84 standards
   */
  public static void validateCoordinate(Coordinate coordinate) throws InvalidGeometryException {
    Objects.requireNonNull(coordinate, "geometry coordinate");
    if (!coordinateIsValid(coordinate)) {
      throw new InvalidGeometryException(String.format("Invalid WGS-84 coordinate: %s", coordinate));
    }
  }
}
