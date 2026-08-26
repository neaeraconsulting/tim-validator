package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrameList;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.timvalidator.config.ValidationOptions;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.road.OverpassRoadGeometryProvider;
import us.dot.its.jpo.timvalidator.road.RoadGeometryProvider;

/**
 * Performs hard-coded best practices validation on TIM messages.
 *
 * Checks for known best practices, field completeness, semantic validity, and other
 * business logic rules beyond basic schema validation.
 */
public class BestPracticesValidator {

    private static final String CHECK_NAME = "Best Practices";

    private final GeometryValidator geometryValidator;
    private final ValidationOptions defaultOptions;
    /**
     * Creates a validator without an external road geometry lookup.
     *
     * This keeps the library deterministic for callers that have not configured a road
     * geometry provider.
     */
    public BestPracticesValidator() {
        this(new OverpassRoadGeometryProvider(), ValidationOptions.networkFree());
    }

    /**
     * Creates a validator that checks heading slices against roadway geometry inside
     * the TIM region.
     *
     * @param roadGeometryProvider provider used to retrieve roadway geometry
     */
    public BestPracticesValidator(RoadGeometryProvider roadGeometryProvider) {
        this(roadGeometryProvider, ValidationOptions.withRoadwayHeading());
    }

    private BestPracticesValidator(
            RoadGeometryProvider roadGeometryProvider,
            ValidationOptions defaultOptions) {
        this.geometryValidator = new GeometryValidator(Objects.requireNonNull(
                roadGeometryProvider,
                "roadGeometryProvider"));
        this.defaultOptions = defaultOptions;
    }

    /**
     * Validates a TIM message against best-practice rules.
     *
     * @param timMessage the TIM message to validate
     * @return structured validation issues (empty list if all checks pass)
     */
    public List<ValidationIssue> validate(Object timMessage) throws ValidationException{
        return validate(timMessage, defaultOptions);
    }

    /**
     * Validates a TIM message and controls whether external roadway geometry is used.
     *
     * @param timMessage the TIM message to validate
     * @param options checks to perform for this validation
     * @return structured validation issues (empty list if all checks pass)
     */
    public List<ValidationIssue> validate(
            Object timMessage,
            ValidationOptions options) throws ValidationException {
        Objects.requireNonNull(options, "options");
        List<ValidationIssue> issues = new ArrayList<>();

        if (timMessage == null) {
            return List.of(error("TIM message is null"));
        }

        Optional<TravelerInformation> travelerInformation = travelerInformation(timMessage);
        if (travelerInformation.isEmpty()) {
            return List.of(error("TIM message is not a TravelerInformationMessageFrame"));
        }
        TravelerInformation tim = travelerInformation.orElseThrow();

        // TODO: Implement best practices checks
        // Example checks to consider:
        // - Verify required fields are present and non-null
        // - Check geographic coordinates are within valid bounds
        // - Validate time ranges and durations are logical
        // - Ensure message IDs are unique and properly sequenced
        // - Check priority levels are appropriate for message type
        // - Validate TIM periods don't exceed reasonable durations
        // - Ensure advisory messages are complete with all required details
        // - Check that frames/extents are properly ordered
        // - Validate region geometries (roads must exist, coordinates valid)

        issues.addAll(validateRequiredFields(tim));
        issues.addAll(validateTimePeriod(tim));
        issues.addAll(validateGeography(tim, options.roadwayHeadingEnabled()));
        issues.addAll(validateAdvisoryContent(tim));

        return List.copyOf(issues);
    }

    private static Optional<TravelerInformation> travelerInformation(Object timMessage) {
        if (timMessage instanceof TravelerInformationMessageFrame messageFrame) {
            return Optional.ofNullable(messageFrame.getValue());
        }

        if (timMessage instanceof MessageFrame<?> messageFrame
                && messageFrame.getValue() instanceof TravelerInformation tim) {
            return Optional.of(tim);
        }

        if (timMessage instanceof TravelerInformation tim) {
            return Optional.of(tim);
        }

        return Optional.empty();
    }

    /** Validates that all required fields are present. */
    private List<ValidationIssue> validateRequiredFields(TravelerInformation tim) {
        // TODO: Check for required fields based on message type

        return List.of();
    }

    /** Validates TIM time period and duration constraints. */
    private List<ValidationIssue> validateTimePeriod(TravelerInformation tim) {
        // TODO: Validate start/end times, ensure they're logical
        // TODO: Check duration doesn't exceed reasonable limits (e.g., 6 months)
        // TODO: Ensure times are in proper sequence

        return List.of();
    }

    /** Validates geographic data in TIM message. */
    private List<ValidationIssue> validateGeography(
            TravelerInformation tim,
            boolean roadwayHeadingEnabled) throws ValidationException {
        List<ValidationIssue> issues = new ArrayList<>();

        // TODO: Validate latitude/longitude ranges
        // TODO: Ensure road identifiers exist and reference valid roads
        // TODO: Check that extent geometries are properly formed
        // TODO: Validate lane numbers and ranges

        TravelerDataFrameList dataFrames = tim.getDataFrames();
        if (dataFrames == null) {
            return issues;
        }

        for (int dataFrameIndex = 0; dataFrameIndex < dataFrames.size(); dataFrameIndex++) {
            TravelerDataFrame dataFrame = dataFrames.get(dataFrameIndex);
            if (dataFrame == null || dataFrame.getRegions() == null) {
                continue;
            }

            TravelerDataFrame.SequenceOfRegions regions = dataFrame.getRegions();
            for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
                GeographicalPath region = regions.get(regionIndex);
                if (region == null) {
                    continue;
                }

                DataFrameIndexes indexes = new DataFrameIndexes(dataFrameIndex, regionIndex);
                issues.addAll(geometryValidator.validate(
                        region,
                        indexes,
                        roadwayHeadingEnabled));
            }
        }

        issues.addAll(LaneCrossingGeometryValidator.validate(dataFrames));

        return List.copyOf(issues);
    }

    /** Validates advisory content and completeness. */
    private List<ValidationIssue> validateAdvisoryContent(TravelerInformation tim) {
        // TODO: Ensure advisory messages have sufficient detail
        // TODO: Validate that message reason codes are appropriate
        // TODO: Check that all required signage frames are provided
        // TODO: Verify message language codes are valid

        return List.of();
    }

    private ValidationIssue error(String message) {
        return new ValidationIssue(ValidationSeverity.ERROR, CHECK_NAME, message, null);
    }
}
