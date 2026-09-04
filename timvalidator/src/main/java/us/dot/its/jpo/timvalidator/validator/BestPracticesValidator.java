package us.dot.its.jpo.timvalidator.validator;

import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import us.dot.its.jpo.asn.j2735.r2024.MessageFrame.MessageFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrameList;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.timvalidator.config.ValidationOptions;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.gnis.CsvGnisFeatureProvider;
import us.dot.its.jpo.timvalidator.gnis.GnisFeatureProvider;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.road.RoadGeometryProvider;

/**
 * Applies TIM time, geometry, and packet-identifier best-practice checks that
 * are not covered by schema validation.
 */
public class BestPracticesValidator {

    private static final String CHECK_NAME = "Best Practices";
    private static final long INDEFINITE_DURATION_MINUTES = 32_000L;

    private static final RoadGeometryProvider UNCONFIGURED_PROVIDER = (location, radiusMeters) -> {
        throw new ValidationException(
                "No RoadGeometryProvider configured; use "
                        + "TimValidationService.withOverpassRoadGeometry(endpoint, userAgent) or "
                        + "the RoadGeometryProvider-accepting constructor to enable "
                        + "roadway-backed checks.");
    };

    private final GeometryValidator geometryValidator;
    private final GnisPacketIdValidator gnisPacketIdValidator;
    private final ValidationOptions defaultOptions;
    private final Clock clock;

    /**
     * Creates a validator with packaged Civil GNIS data and without an external
     * road geometry lookup.
     *
     * This keeps the library deterministic for callers that have not configured a road
     * geometry provider.
     */
    public BestPracticesValidator() {
        this(
                UNCONFIGURED_PROVIDER,
                new CsvGnisFeatureProvider(),
                ValidationOptions.networkFree(),
                Clock.systemUTC());
    }

    /**
     * Creates a validator whose time-based checks use the supplied custom clock.
     *
     * @param clock time source used to determine the current year
     */
    public BestPracticesValidator(Clock clock) {
        this(
                UNCONFIGURED_PROVIDER,
                new CsvGnisFeatureProvider(),
                ValidationOptions.networkFree(),
                clock);
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
                new CsvGnisFeatureProvider(),
                ValidationOptions.withRoadwayHeading(),
                Clock.systemUTC());
    }

    /**
     * Creates a network-free validator with an injected GNIS data source.
     *
     * @param gnisFeatureProvider provider used to retrieve GNIS features
     */
    public BestPracticesValidator(GnisFeatureProvider gnisFeatureProvider) {
        this(
                UNCONFIGURED_PROVIDER,
                Objects.requireNonNull(gnisFeatureProvider, "gnisFeatureProvider"),
                ValidationOptions.networkFree(),
                Clock.systemUTC());
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
        this(
                roadGeometryProvider,
                gnisFeatureProvider,
                ValidationOptions.withRoadwayHeading(),
                Clock.systemUTC());
    }

    private BestPracticesValidator(
            RoadGeometryProvider roadGeometryProvider,
            GnisFeatureProvider gnisFeatureProvider,
            ValidationOptions defaultOptions,
            Clock clock) {
        this.geometryValidator = new GeometryValidator(Objects.requireNonNull(
                roadGeometryProvider,
                "roadGeometryProvider"));
        this.gnisPacketIdValidator = new GnisPacketIdValidator(Objects.requireNonNull(
                gnisFeatureProvider,
                "gnisFeatureProvider"));
        this.defaultOptions = defaultOptions;
        this.clock = Objects.requireNonNull(clock, "clock");
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
        issues.addAll(validateStartYears(clock, tim));
        issues.addAll(validateDefiniteEndTimes(tim));
        issues.addAll(validateGeometry(tim, options));

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

    /** Warns when a data frame is assigned a start year later than the current UTC year. */
    private static List<ValidationIssue> validateStartYears(
            Clock clock,
            TravelerInformation tim) {
        TravelerDataFrameList dataFrames = tim.getDataFrames();
        if (dataFrames == null) {
            return List.of();
        }

        int currentYear = Year.now(clock).getValue();
        List<ValidationIssue> issues = new ArrayList<>();
        for (int index = 0; index < dataFrames.size(); index++) {
            TravelerDataFrame dataFrame = dataFrames.get(index);
            if (dataFrame == null || dataFrame.getStartYear() == null) {
                continue;
            }

            long startYear = dataFrame.getStartYear().getValue();
            if (startYear > currentYear) {
                issues.add(warning(
                        String.format(
                                Locale.ROOT,
                                "Data frame %d startYear %d is later than the current UTC year %d",
                                index,
                                startYear,
                                currentYear),
                        dataFramePath(index) + "/startYear"));
            }
        }
        return List.copyOf(issues);
    }

    /** Warns when durationTime uses the value representing an indefinite end time. */
    private static List<ValidationIssue> validateDefiniteEndTimes(TravelerInformation tim) {
        TravelerDataFrameList dataFrames = tim.getDataFrames();
        if (dataFrames == null) {
            return List.of();
        }

        List<ValidationIssue> issues = new ArrayList<>();
        for (int index = 0; index < dataFrames.size(); index++) {
            TravelerDataFrame dataFrame = dataFrames.get(index);
            if (dataFrame == null || dataFrame.getDurationTime() == null
                    || dataFrame.getDurationTime().getValue() != INDEFINITE_DURATION_MINUTES) {
                continue;
            }

            issues.add(warning(
                    String.format(
                            Locale.ROOT,
                            "Data frame %d durationTime 32000 represents an indefinite end time which is not recommended",
                            index),
                    dataFramePath(index) + "/durationTime"));
        }
        return List.copyOf(issues);
    }

    /** Builds the indexed path prefix needed for warnings produced inside data-frame loops. */
    private static String dataFramePath(int dataFrameIndex) {
        return "/value/TravelerInformation/dataFrames/" + dataFrameIndex;
    }

    private List<ValidationIssue> validateGeometry(
            TravelerInformation tim,
            ValidationOptions options) throws ValidationException {
        List<ValidationIssue> issues = new ArrayList<>();

        TravelerDataFrameList dataFrames = tim.getDataFrames();
        if (dataFrames == null) {
            return issues;
        }

        for (var region : DataFrameRegion.regions(dataFrames).toList()) {
            issues.addAll(geometryValidator.validate(
                region.path(),
                region.indexes(),
                options));
        }

        issues.addAll(LaneCrossingGeometryValidator.validate(dataFrames));

        return List.copyOf(issues);
    }

    private static ValidationIssue error(String message) {
        return new ValidationIssue(ValidationSeverity.ERROR, CHECK_NAME, message, null);
    }

    private static ValidationIssue warning(String message, String path) {
        return new ValidationIssue(ValidationSeverity.WARNING, CHECK_NAME, message, path);
    }
}
