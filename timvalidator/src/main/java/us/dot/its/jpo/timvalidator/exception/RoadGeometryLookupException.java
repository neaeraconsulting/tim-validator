package us.dot.its.jpo.timvalidator.exception;

/** Indicates that external roadway geometry could not be retrieved or decoded. */
public class RoadGeometryLookupException extends ValidationException {

    /**
     * Constructs a new RoadGeometryLookupException with the specified detail message.
     * @param message the detail message for the exception
     */
    public RoadGeometryLookupException(String message) {
        super(message);
    }

    /**
     * Constructs a new RoadGeometryLookupException with the specified detail message and cause.
     * @param message the detail message for the exception
     * @param cause the cause of the exception
     */
    public RoadGeometryLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
