package us.dot.its.jpo.timvalidator.gnis;

import java.util.List;
import java.util.Optional;

/** Supplies approved GNIS features by identifier and geographic bounds. */
public interface GnisFeatureProvider {

    Optional<GnisFeature> findById(int gnisId) throws GnisLookupException;

    List<GnisFeature> findWithin(GnisBounds bounds) throws GnisLookupException;
}
