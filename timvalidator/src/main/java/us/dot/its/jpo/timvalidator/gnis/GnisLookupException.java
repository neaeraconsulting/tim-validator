package us.dot.its.jpo.timvalidator.gnis;

/** Indicates that a GNIS lookup could not be completed or decoded. */
public class GnisLookupException extends Exception {

    public GnisLookupException(String message) {
        super(message);
    }

    public GnisLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
