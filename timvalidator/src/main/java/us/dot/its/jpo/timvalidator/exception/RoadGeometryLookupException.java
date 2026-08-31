package us.dot.its.jpo.timvalidator.exception;

/** Indicates that external roadway geometry could not be retrieved or decoded. */
public class RoadGeometryLookupException extends ValidationException {

    public RoadGeometryLookupException(String message) {
        super(message);
    }

    public RoadGeometryLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
