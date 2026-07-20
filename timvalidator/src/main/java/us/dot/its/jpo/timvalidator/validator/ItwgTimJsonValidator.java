package us.dot.its.jpo.timvalidator.validator;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Validates TIM messages against the stricter ITWG profile schema.
 */
public class ItwgTimJsonValidator extends TimJsonValidator {

    private static final Resource ITWG_TIM_SCHEMA_RESOURCE =
        new ClassPathResource("us/dot/its/jpo/timvalidator/TravelerInformationMessageFrameITWG.schema.json");

    public ItwgTimJsonValidator() {
        super(ITWG_TIM_SCHEMA_RESOURCE);
    }

    @Override
    protected String getCheckName() {
        return "ITWG Schema Validation";
    }
}
