package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.networknt.schema.Error;

import us.dot.its.jpo.asn.j2735.r2024.ITIS.ITIScodesAndText;
import us.dot.its.jpo.asn.j2735.r2024.ITIS.ITIScodesAndTextSequence;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;

/**
 * Validates normalized TIM ITIS message content with JSON Schema.
 *
 * <p>
 * Extracts advisory ITIS content from each TIM data frame, normalizes it into
 * the small object expected by {@code ITISCodes.json}, and validates that object.
 */
public class ItisJsonValidator extends AbstractJsonValidator {

    private static final Resource ITIS_SCHEMA_RESOURCE =
        new ClassPathResource("us/dot/its/jpo/timvalidator/ITISCodes.json");
    private static final String CHECK_NAME = "ITIS Content Validation";

    public ItisJsonValidator() {
        super(ITIS_SCHEMA_RESOURCE);
    }

    /**
     * Extracts and validates advisory ITIS content from a deserialized TIM message frame.
     */
    @Override
    public void validate(Object content) throws ValidationException {
        validateAndCollectWarnings(content);
    }

    /**
     * Validates eligible advisory ITIS content and returns non-blocking warnings for skipped frames.
     */
    public List<ValidationIssue> validateAndCollectWarnings(Object content) throws ValidationException {
        if (!(content instanceof TravelerInformationMessageFrame messageFrame)) {
            throw new ValidationException("Expected TravelerInformationMessageFrame payload for ITIS validation");
        }

        List<ValidationIssue> warnings = new ArrayList<>();
        if (messageFrame.getValue() == null || messageFrame.getValue().getDataFrames() == null) {
            warnings.add(warning(
                "No data frames available for ITIS pattern validation.",
                "/value/TravelerInformation/dataFrames"));
            return warnings;
        }

        int eligibleFrameCount = 0;
        int frameIndex = 0;
        for (TravelerDataFrame dataFrame : messageFrame.getValue().getDataFrames()) {
            Optional<Map<String, Object>> normalizedContent = normalizeDataFrame(dataFrame, frameIndex, warnings);
            if (normalizedContent.isPresent()) {
                eligibleFrameCount++;
                super.validate(normalizedContent.get());
            }
            frameIndex++;
        }

        if (eligibleFrameCount == 0) {
            warnings.add(warning(
                "No data frame contained numeric advisory ITIS codes eligible for ITIS pattern validation.",
                "/value/TravelerInformation/dataFrames"));
        }

        return warnings;
    }

    /**
     * Formats ITIS schema errors with compact field paths such as /itis.
     */
    @Override
    protected String formatValidationErrors(List<Error> validationErrors) {
        return validationErrors.stream()
            .map(error -> error.getInstanceLocation() + ": " + error.getMessage())
            .reduce((left, right) -> left + "; " + right)
            .orElse("ITIS content validation failed");
    }

    /**
     * Converts advisory content into the normalized object expected by ITISCodes.json.
     */
    private Optional<Map<String, Object>> normalizeDataFrame(
            TravelerDataFrame dataFrame,
            int frameIndex,
            List<ValidationIssue> warnings) {
        if (dataFrame == null) {
            warnings.add(warning("Data frame " + frameIndex + " is null; ITIS pattern validation skipped.", dataFramePath(frameIndex)));
            return Optional.empty();
        }

        if (dataFrame.getContent() == null) {
            warnings.add(warning(
                "Data frame " + frameIndex + " has no content; ITWG recommends advisory content for TIM messages.",
                contentPath(frameIndex)));
            return Optional.empty();
        }

        if (dataFrame.getContent().getAdvisory() == null) {
            warnings.add(warning(
                "Data frame " + frameIndex + " uses non-advisory content; ITIS pattern validation only evaluates advisory content.",
                contentPath(frameIndex)));
            return Optional.empty();
        }

        ITIScodesAndText advisory = dataFrame.getContent().getAdvisory();
        List<Integer> itisCodes = extractItisCodes(advisory);
        if (itisCodes.isEmpty()) {
            if (advisory.isEmpty()) {
                warnings.add(warning(
                    "Data frame " + frameIndex + " has empty advisory content; ITIS pattern validation skipped.",
                    advisoryPath(frameIndex)));
            } else {
                warnings.add(warning(
                    "Data frame " + frameIndex + " advisory content contains no numeric ITIS codes; ITIS pattern validation skipped.",
                    advisoryPath(frameIndex)));
            }
            return Optional.empty();
        }

        Map<String, Object> normalizedContent = new LinkedHashMap<>();
        normalizedContent.put("itis", itisCodes);
        return Optional.of(normalizedContent);
    }

    @Override
    protected String getCheckName() {
        return CHECK_NAME;
    }

    private ValidationIssue warning(String message, String path) {
        return new ValidationIssue(ValidationSeverity.WARNING, CHECK_NAME, message, path);
    }

    private String dataFramePath(int frameIndex) {
        return "/value/TravelerInformation/dataFrames/" + frameIndex;
    }

    private String contentPath(int frameIndex) {
        return dataFramePath(frameIndex) + "/content";
    }

    private String advisoryPath(int frameIndex) {
        return contentPath(frameIndex) + "/advisory";
    }

    /**
     * Extracts numeric ITIS code choices from an advisory phrase.
     */
    private List<Integer> extractItisCodes(ITIScodesAndText advisory) {
        List<Integer> itisCodes = new ArrayList<>();
        for (ITIScodesAndTextSequence sequence : advisory) {
            if (sequence == null || sequence.getItem() == null || sequence.getItem().getItis() == null) {
                continue;
            }
            itisCodes.add(Math.toIntExact(sequence.getItem().getItis().getValue()));
        }
        return itisCodes;
    }
}
