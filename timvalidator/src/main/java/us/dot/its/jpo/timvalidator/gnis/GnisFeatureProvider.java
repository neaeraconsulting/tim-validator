package us.dot.its.jpo.timvalidator.gnis;

import java.util.Optional;

/**
 * Supplies approved Civil GNIS features by identifier.
 *
 * <p>An implementation must return only a Civil feature whose
 * {@link GnisFeature#id()} equals the requested identifier. Returning an empty
 * optional means that the identifier is not in the provider's approved Civil
 * feature set; lookup or source-data failures should throw
 * {@link GnisLookupException}.</p>
 */
public interface GnisFeatureProvider {

    /**
     * Finds the approved Civil feature with the requested identifier.
     *
     * @param gnisId unsigned 24-bit GNIS identifier
     * @return the matching feature, or empty when the identifier is not approved
     * @throws GnisLookupException when the source cannot be loaded or interpreted
     */
    Optional<GnisFeature> findById(int gnisId) throws GnisLookupException;
}
