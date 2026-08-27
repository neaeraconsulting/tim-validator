package us.dot.its.jpo.timvalidator.road;

import java.util.Objects;
import org.locationtech.jts.geom.Coordinate;
import us.dot.its.jpo.timvalidator.exception.InvalidGeometryException;

public final class GeoUtils {

  public static boolean coordinateIsValid(Coordinate coordinate) {
    return (coordinate.isValid()
        || coordinate.getY() >= -90.0
        || coordinate.getY() <= 90.0
        || coordinate.getX() >= -180.0
        || coordinate.getX() <= 180.0);
  }

  public static void validateCoordinate(Coordinate coordinate) throws InvalidGeometryException {
    Objects.requireNonNull(coordinate, "geometry coordinate");
    if (!coordinateIsValid(coordinate)) {
      throw new InvalidGeometryException(String.format("Invalid WGS-84 coordinate: %s", coordinate));
    }
  }
}
