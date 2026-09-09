package us.dot.its.jpo.timvalidator.validator;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Validates TIM messages against the stricter ITWG profile schema.
 */
public class ItwgTimJsonValidator extends TimJsonValidator {

    /**
     * Constructs an instance of the ITWG TIM JSON validator using the ITWG schema.
     * Default ITWG Schema is located here: us/dot/its/jpo/timvalidator/TravelerInformationMessageFrameITWG.schema.json
     */
    private static final Resource ITWG_TIM_SCHEMA_RESOURCE =
        new ClassPathResource("us/dot/its/jpo/timvalidator/TravelerInformationMessageFrameITWG.schema.json");

    /**
     * Constructs an instance of the ITWG TIM JSON validator using the default ITWG schema.
     */
    public ItwgTimJsonValidator() {
        super(ITWG_TIM_SCHEMA_RESOURCE);
    }

    /**
     * Returns the name of the check performed by this validator.
     * @see us.dot.its.jpo.timvalidator.validator.TimJsonValidator#getCheckName()
     */
    @Override
    protected String getCheckName() {
        return "ITWG Schema Validation";
    }
}
