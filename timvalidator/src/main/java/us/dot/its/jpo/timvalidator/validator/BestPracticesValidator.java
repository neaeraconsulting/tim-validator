package us.dot.its.jpo.timvalidator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrameList;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.road.RoadGeometryProvider;

/**
 * Performs hard-coded best practices validation on TIM messages.
 *
 * Checks for known best practices, field completeness, semantic validity, and other
 * business logic rules beyond basic schema validation.
 */
public class BestPracticesValidator {

    private static final String CHECK_NAME = "Best Practices";
    private static final double DEFAULT_ROAD_SEARCH_RADIUS_METERS = 30.0;

    private final HeadingSliceRoadGeometryValidator headingSliceRoadGeometryValidator;

    /**
     * Creates a validator without an external road geometry lookup.
     *
     * This keeps the library deterministic for callers that have not configured a road
     * geometry provider.
     */
    public BestPracticesValidator() {
        this.headingSliceRoadGeometryValidator = null;
    }

    /**
     * Creates a validator that checks heading slices against nearby roadway geometry.
     *
     * @param roadGeometryProvider provider used to retrieve nearby roadway geometry
     */
    public BestPracticesValidator(RoadGeometryProvider roadGeometryProvider) {
        this(roadGeometryProvider, DEFAULT_ROAD_SEARCH_RADIUS_METERS);
    }

    /**
     * Creates a validator that checks heading slices against nearby roadway geometry.
     *
     * @param roadGeometryProvider provider used to retrieve nearby roadway geometry
     * @param roadSearchRadiusMeters maximum distance from the TIM start to consider
     */
    public BestPracticesValidator(
            RoadGeometryProvider roadGeometryProvider,
            double roadSearchRadiusMeters) {
        this.headingSliceRoadGeometryValidator =
                new HeadingSliceRoadGeometryValidator(roadGeometryProvider, roadSearchRadiusMeters);
    }

    /**
     * Creates a validator that checks heading slices against a filtered set of nearby roads.
     *
     * @param roadGeometryProvider provider used to retrieve nearby roadway geometry
     * @param roadSearchRadiusMeters maximum distance from the TIM start to consider
     * @param candidateDistanceToleranceMeters maximum additional distance from the
     *        closest road for another road to remain an intersection candidate
     */
    public BestPracticesValidator(
            RoadGeometryProvider roadGeometryProvider,
            double roadSearchRadiusMeters,
            double candidateDistanceToleranceMeters) {
        this.headingSliceRoadGeometryValidator =
                new HeadingSliceRoadGeometryValidator(
                        roadGeometryProvider,
                        roadSearchRadiusMeters,
                        candidateDistanceToleranceMeters);
    }

    /**
     * Validates a TIM message against best practices rules.
     *
     * @param timMessage the TIM message to validate
     * @return list of validation issues found (empty list if all checks pass)
     */
    public List<String> validate(Object timMessage) {
        return validateAndCollectIssues(timMessage).stream()
                .map(ValidationIssue::message)
                .toList();
    }

    /**
     * Validates a TIM message and returns structured issues suitable for an API response.
     *
     * @param timMessage the TIM message to validate
     * @return structured validation issues (empty list if all checks pass)
     */
    public List<ValidationIssue> validateAndCollectIssues(Object timMessage) {
        List<ValidationIssue> structuredIssues = new ArrayList<>();
        validateBuiltInRules(timMessage).stream()
                .map(message -> new ValidationIssue(
                        ValidationSeverity.ERROR,
                        CHECK_NAME,
                        message,
                        null))
                .forEach(structuredIssues::add);

        Optional<TravelerInformation> tim = travelerInformation(timMessage);
        if (headingSliceRoadGeometryValidator != null && tim.isPresent()) {
            structuredIssues.addAll(validateHeadingSlices(tim.orElseThrow()));
        }

        return List.copyOf(structuredIssues);
    }

    private List<String> validateBuiltInRules(Object timMessage) {
        List<String> issues = new ArrayList<>();

        if (timMessage == null) {
            issues.add("TIM message is null");
            return issues;
        }

        Optional<TravelerInformation> travelerInformation = travelerInformation(timMessage);
        if (travelerInformation.isEmpty()) {
            issues.add("TIM message is not a TravelerInformationMessageFrame");
            return issues;
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
        issues.addAll(validateGeography(tim));
        issues.addAll(validateAdvisoryContent(tim));

        return issues;
    }

    private List<ValidationIssue> validateHeadingSlices(TravelerInformation tim) {
        List<ValidationIssue> issues = new ArrayList<>();
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
                if (region != null) {
                    issues.addAll(headingSliceRoadGeometryValidator.validate(
                            region,
                            dataFrameIndex,
                            regionIndex));
                }
            }
        }

        return issues;
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
    private List<String> validateRequiredFields(TravelerInformation tim) {
        List<String> issues = new ArrayList<>();

        // TODO: Check for required fields based on message type

        return issues;
    }

    /** Validates TIM time period and duration constraints. */
    private List<String> validateTimePeriod(TravelerInformation tim) {
        List<String> issues = new ArrayList<>();

        // TODO: Validate start/end times, ensure they're logical
        // TODO: Check duration doesn't exceed reasonable limits (e.g., 6 months)
        // TODO: Ensure times are in proper sequence

        return issues;
    }

    /** Validates geographic data in TIM message. */
    private List<String> validateGeography(TravelerInformation tim) {
        List<String> issues = new ArrayList<>();

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
                issues.addAll(GeometryValidator.validate(region, indexes));
            }
        }

        return issues;
    }

    /** Validates advisory content and completeness. */
    private List<String> validateAdvisoryContent(TravelerInformation tim) {
        List<String> issues = new ArrayList<>();

        // TODO: Ensure advisory messages have sufficient detail
        // TODO: Validate that message reason codes are appropriate
        // TODO: Check that all required signage frames are provided
        // TODO: Verify message language codes are valid

        return issues;
    }
}
