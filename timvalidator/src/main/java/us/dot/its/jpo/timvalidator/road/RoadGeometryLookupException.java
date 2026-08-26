package us.dot.its.jpo.timvalidator.road;

import us.dot.its.jpo.timvalidator.exception.ValidationException;

/** Indicates that external roadway geometry could not be retrieved or decoded. */
public class RoadGeometryLookupException extends ValidationException {

    public RoadGeometryLookupException(String message) {
        super(message);
    }

    public RoadGeometryLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
