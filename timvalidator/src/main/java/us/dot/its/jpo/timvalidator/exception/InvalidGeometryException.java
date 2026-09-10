package us.dot.its.jpo.timvalidator.exception;
/**
 * Custom exception for coordinate validation errors
 */
public class InvalidGeometryException extends ValidationException {

  /**
   * Constructs an InvalidGeometryException with the specified detail message.
   *
   * @param message the detail message.
   */
  public InvalidGeometryException(String message) {
    super(message);
  }

  /**
   * Constructs an InvalidGeometryException with the specified detail message and cause.
   *
   * @param message the detail message.
   * @param cause the cause of the exception.
   */
  public InvalidGeometryException(String message, Throwable cause) {
    super(message, cause);
  }

}
