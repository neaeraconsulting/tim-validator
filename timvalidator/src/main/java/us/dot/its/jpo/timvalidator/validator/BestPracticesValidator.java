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
import us.dot.its.jpo.timvalidator.gnis.GeoPackageGnisFeatureProvider;
import us.dot.its.jpo.timvalidator.gnis.GnisFeatureProvider;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.road.OverpassRoadGeometryProvider;
import us.dot.its.jpo.timvalidator.road.RoadGeometryProvider;

/**
 * Applies TIM geometry and packet-identifier best-practice checks that are not
 * covered by schema validation.
 */
public class BestPracticesValidator {

    private static final String CHECK_NAME = "Best Practices";

    private final GeometryValidator geometryValidator;
    private final GnisPacketIdValidator gnisPacketIdValidator;
    private final ValidationOptions defaultOptions;

    /**
     * Creates a validator with packaged Civil GNIS data and without an external
     * road geometry lookup.
     *
     * This keeps the library deterministic for callers that have not configured a road
     * geometry provider.
     */
    public BestPracticesValidator() {
        this(
                new OverpassRoadGeometryProvider(),
                new GeoPackageGnisFeatureProvider(),
                ValidationOptions.networkFree());
    }

    /**
     * Creates a validator that checks heading slices against roadway geometry inside
     * the TIM region.
     *
     * @param roadGeometryProvider provider used to retrieve roadway geometry
     */
    public BestPracticesValidator(RoadGeometryProvider roadGeometryProvider) {
        this(
                roadGeometryProvider,
                new GeoPackageGnisFeatureProvider(),
                ValidationOptions.withRoadwayHeading());
    }

    /**
     * Creates a validator with injectable roadway and GNIS data sources.
     *
     * @param roadGeometryProvider provider used to retrieve roadway geometry
     * @param gnisFeatureProvider provider used to retrieve GNIS features
     */
    public BestPracticesValidator(
            RoadGeometryProvider roadGeometryProvider,
            GnisFeatureProvider gnisFeatureProvider) {
        this(roadGeometryProvider, gnisFeatureProvider, ValidationOptions.withRoadwayHeading());
    }

    private BestPracticesValidator(
            RoadGeometryProvider roadGeometryProvider,
            GnisFeatureProvider gnisFeatureProvider,
            ValidationOptions defaultOptions) {
        this.geometryValidator = new GeometryValidator(Objects.requireNonNull(
                roadGeometryProvider,
                "roadGeometryProvider"));
        this.gnisPacketIdValidator = new GnisPacketIdValidator(Objects.requireNonNull(
                gnisFeatureProvider,
                "gnisFeatureProvider"));
        this.defaultOptions = defaultOptions;
    }

    /**
     * Validates a TIM message against best-practice rules.
     *
     * @param timMessage the TIM message to validate
     * @return structured validation issues (empty list if all checks pass)
     */
    public List<ValidationIssue> validate(Object timMessage) throws ValidationException {
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

        issues.addAll(gnisPacketIdValidator.validate(tim));
        issues.addAll(validateGeometry(tim, options.roadwayHeadingEnabled()));

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

    private List<ValidationIssue> validateGeometry(
            TravelerInformation tim,
            boolean roadwayHeadingEnabled) throws ValidationException {
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

    private static ValidationIssue error(String message) {
        return new ValidationIssue(ValidationSeverity.ERROR, CHECK_NAME, message, null);
    }
}
