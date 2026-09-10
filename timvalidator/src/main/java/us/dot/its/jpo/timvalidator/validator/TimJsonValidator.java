package us.dot.its.jpo.timvalidator.validator;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;
import us.dot.its.jpo.timvalidator.exception.ValidationException;


/**
 * Validates JSON representations of J2735 TIM (Traveler Information Message) MessageFrames against the corresponding JSON schema.
 * This class provides methods to perform schema validation specifically for TIM JSON payloads. 
 * This class also provides some simple high level nullity and validation checks to ensure TIM can be properly passed to schema for validation.
 */
public class TimJsonValidator extends AbstractJsonValidator {

    /**
     * The resource representing the JSON schema for J2735 TIM MessageFrames. This should point to the json schema file location that will be used for validation.
     * Default schema resource used for TIM JSON validation is located here: "schemas/TravelerInformation/TravelerInformationMessageFrame.schema.json"
     */
    private static final Resource TIM_SCHEMA_RESOURCE =
        new ClassPathResource("schemas/TravelerInformation/TravelerInformationMessageFrame.schema.json");

    /**
     * Initializes a new instance of the TimJsonValidator with the default TIM JSON schema resource.
     */
    public TimJsonValidator() {
        this(TIM_SCHEMA_RESOURCE);
    }

    /**
     * Initializes a new instance of the TimJsonValidator with a custom JSON schema resource.
     * @param schemaResource the custom JSON schema resource to use for TIM validation
     */
    protected TimJsonValidator(Resource schemaResource) {
        super(schemaResource);
    }

    /**
     * Returns the name of the validation check performed by this validator.
     * @see us.dot.its.jpo.timvalidator.validator.AbstractJsonValidator#getCheckName()
     */
    @Override
    protected String getCheckName() {
        return "J2735 Schema Validation";
    }

    /**
     * Applies J2735 TIM-specific type/message-id guards before delegating to generic schema validation.
     * @see us.dot.its.jpo.timvalidator.validator.AbstractJsonValidator#validate(Object)
     */
    @Override
    public void validate(Object messageFramePayload) throws ValidationException {
        validateMessageFramePayload(messageFramePayload);
        super.validate(messageFramePayload);
    }

    /**
     * Validates the TIM message frame payload for nullity, type, and message ID before schema validation.
     * @param messageFramePayload the TIM message frame payload to validate
     * @throws ValidationException if the payload is null, not a MessageFrame, or has an unsupported message ID (not 31)
     */
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
