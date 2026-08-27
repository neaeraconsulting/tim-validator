package us.dot.its.jpo.timvalidator.exception;

import lombok.Getter;
import org.locationtech.jts.geom.Coordinate;

/**
 * Custom exception for coordinate validation errors
 */
public class InvalidGeometryException extends ValidationException {

  public InvalidGeometryException(String message) {
    super(message);
  }

  public InvalidGeometryException(String message, Throwable cause) {
    super(message, cause);
  }

}
