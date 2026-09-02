package us.dot.its.jpo.timvalidator.gnis;

import java.util.Optional;

/** Supplies approved GNIS features by identifier. */
public interface GnisFeatureProvider {

    Optional<GnisFeature> findById(int gnisId) throws GnisLookupException;
}
