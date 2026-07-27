package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

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

        List<ValidationIssue> issues = new ArrayList<>();
        if (messageFrame.getValue() == null || messageFrame.getValue().getDataFrames() == null) {
            issues.add(warning(
                "No data frames available for ITIS pattern validation.",
                "/value/TravelerInformation/dataFrames"));
            return issues;
        }

        int eligibleFrameCount = 0;
        int frameIndex = 0;
        for (TravelerDataFrame dataFrame : messageFrame.getValue().getDataFrames()) {
            Optional<Map<String, Object>> normalizedContent = normalizeDataFrame(dataFrame, frameIndex, issues);
            if (normalizedContent.isPresent()) {
                eligibleFrameCount++;
                try {
                    validateNormalizedContent(normalizedContent.get(), frameIndex);
                } catch (ValidationException ex) {
                    if (ex.getIssues().isEmpty()) {
                        throw ex;
                    }
                    issues.addAll(ex.getIssues());
                }
            }
            frameIndex++;
        }

        if (eligibleFrameCount == 0) {
            issues.add(warning(
                "No data frame contained numeric advisory ITIS codes eligible for ITIS pattern validation.",
                "/value/TravelerInformation/dataFrames"));
        }

        List<ValidationIssue> errors = issues.stream()
            .filter(issue -> issue.severity() == ValidationSeverity.ERROR)
            .toList();
        if (!errors.isEmpty()) {
            String message = String.join(" ", errors.stream()
                .map(ValidationIssue::message)
                .toList());
            throw new ValidationException(message, issues);
        }

        return issues;
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

    /**
     * Collapses the implementation-specific errors produced by the schema's
     * {@code oneOf} branches into one domain-level issue for the source frame.
     */
    private void validateNormalizedContent(Map<String, Object> normalizedContent, int frameIndex)
            throws ValidationException {
        try {
            super.validate(normalizedContent);
        } catch (ValidationException ex) {
            if (ex.getIssues().isEmpty()) {
                throw ex;
            }

            String message = "ITIS sequence does not match any supported pattern: "
                + normalizedContent.get("itis") + ".";
            ValidationIssue issue = new ValidationIssue(
                ValidationSeverity.ERROR,
                CHECK_NAME,
                message,
                advisoryPath(frameIndex));
            throw new ValidationException(message, List.of(issue));
        }
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
