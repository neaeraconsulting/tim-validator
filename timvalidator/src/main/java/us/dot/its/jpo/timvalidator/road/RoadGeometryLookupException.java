package us.dot.its.jpo.timvalidator.road;

/** Indicates that external roadway geometry could not be retrieved or decoded. */
public class RoadGeometryLookupException extends RuntimeException {

    public RoadGeometryLookupException(String message) {
        super(message);
    }

    public RoadGeometryLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
