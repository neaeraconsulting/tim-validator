package us.dot.its.jpo.timvalidator.validator;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;
import us.dot.its.jpo.timvalidator.exception.ValidationException;

public class TimJsonValidator extends AbstractJsonValidator {

    private static final Resource TIM_SCHEMA_RESOURCE =
        new ClassPathResource("schemas/TravelerInformation/TravelerInformationMessageFrame.schema.json");

    public TimJsonValidator() {
        this(TIM_SCHEMA_RESOURCE);
    }

    protected TimJsonValidator(Resource schemaResource) {
        super(schemaResource);
    }

    @Override
    protected String getCheckName() {
        return "J2735 Schema Validation";
    }

    /**
     * Applies J2735 TIM-specific type/message-id guards before delegating to generic schema validation.
     */
    @Override
    public void validate(Object messageFramePayload) throws ValidationException {
        validateMessageFramePayload(messageFramePayload);
        super.validate(messageFramePayload);
    }

    protected void validateMessageFramePayload(Object messageFramePayload) throws ValidationException {
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
    }

    /**
     * Extracts the numeric message id for J2735 TIM type.
     */
    private Integer extractMessageId(MessageFrame<?> messageFrame) {
        if (messageFrame.getMessageId() == null) {
            return null;
        }
        return Math.toIntExact(messageFrame.getMessageId().getValue());
    }
}
