package us.dot.its.jpo.timvalidator.gnis;

/** Indicates that a GNIS lookup could not be completed or decoded. */
public class GnisLookupException extends Exception {

    /**
     * Constructs a new GnisLookupException with the specified detail message.
     *
     * @param message the detail message
     */
    public GnisLookupException(String message) {
        super(message);
    }

    /**
     * Constructs a new GnisLookupException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public GnisLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
