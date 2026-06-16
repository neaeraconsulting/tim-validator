package us.dot.its.jpo.timvalidator.validator;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;
import us.dot.its.jpo.timvalidator.exception.ValidationException;

public class TimJsonValidator extends AbstractJsonValidator {

    private static final Resource TIM_SCHEMA_RESOURCE = new ClassPathResource("schemas/tim.schema.json");
    private static final String TIM_SCHEMA_REF_RESOURCE = "us/dot/its/jpo/timvalidator/TravelerInformationMessageFrame.schema.json";

    public TimJsonValidator() {
        super(TIM_SCHEMA_RESOURCE);
    }

    /**
     * Applies J2735 TIM-specific type/message-id guards before delegating to generic schema validation.
     */
    @Override
    public void validate(Object messageFramePayload) throws ValidationException {
        if (messageFramePayload == null) {
            throw new ValidationException("TIM message cannot be null");
        }

        if (!(messageFramePayload instanceof MessageFrame<?> messageFrame)) {
            throw new ValidationException("Expected MessageFrame payload for TIM validation");
        }

        Integer messageId = extractMessageId(messageFrame);
        if (messageId == null || messageId != 31) {
            throw new ValidationException("Unsupported message type; only J2735 TIM MessageFrame (messageId=31) is supported");
        }

        super.validate(messageFrame);
    }

    /**
     * Rewrites the external J2735 TIM schema reference to a bundled classpath schema for offline resolution.
     */
    @Override
    protected String preprocessSchemaJson(String schemaJson) {
        return schemaJson.replace(
            "https://github.com/usdot-jpo-ode/jpo-asn-pojos/blob/jpo-asn-pojos-1.2.0/jpo-asn-jsonschema-generator/src/main/resources/schemas/TravelerInformation/TravelerInformationMessageFrame.schema.json",
            "classpath:/" + TIM_SCHEMA_REF_RESOURCE
        );
    }

    /**
     * Extracts the numeric message id for J2735 TIM type gating in this concrete validator.
     */
    private Integer extractMessageId(MessageFrame<?> messageFrame) {
        if (messageFrame.getMessageId() == null) {
            return null;
        }
        return Math.toIntExact(messageFrame.getMessageId().getValue());
    }
}
