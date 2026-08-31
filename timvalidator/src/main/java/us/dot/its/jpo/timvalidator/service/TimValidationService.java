package us.dot.its.jpo.timvalidator.service;

import java.util.List;
import java.util.Objects;

import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.timvalidator.config.ValidationOptions;
import us.dot.its.jpo.timvalidator.converter.JerToMessageFrameConverter;
import us.dot.its.jpo.timvalidator.converter.UperToMessageFrameConverter;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationResult;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;
import us.dot.its.jpo.timvalidator.road.OverpassRoadGeometryProvider;
import us.dot.its.jpo.timvalidator.road.RoadGeometryProvider;
import us.dot.its.jpo.timvalidator.validator.BestPracticesValidator;
import us.dot.its.jpo.timvalidator.validator.ItisJsonValidator;
import us.dot.its.jpo.timvalidator.validator.ItwgTimJsonValidator;
import us.dot.its.jpo.timvalidator.validator.TimJsonValidator;

/**
 * Main service orchestrating the TIM validation pipeline.
 * 
 * Process flow:
 * 1. Convert UPER hex string to XER (XML)
 * 2. Deserialize XER into a typed TravelerInformationMessageFrame POJO
 * 3. Perform schema validation
 * 4. Execute ITIS content validation
 * 5. Execute best practices checks
 * 6. Return processed validation result
 */
public class TimValidationService {

    private final UperToMessageFrameConverter uperToMessageFrameConverter;
    private final JerToMessageFrameConverter jerToMessageFrameConverter;
    private final TimJsonValidator j2735SchemaValidator;
    private final ItwgTimJsonValidator itwgSchemaValidator;
    private final ItisJsonValidator itisContentValidator;
    private final BestPracticesValidator bestPracticesValidator;
    private final ValidationOptions defaultOptions;

    /** Creates a service whose default validation is network-free. */
    public TimValidationService() {
        this(new BestPracticesValidator(), ValidationOptions.defaults());
    }

    /**
     * Creates a validation service with an Overpass-backed roadway heading check against a
     * caller-chosen Overpass endpoint and User-Agent.
     *
     * <p>The Overpass API's public instances are shared, rate-limited infrastructure; see the
     * usage guidelines at
     * <a href="https://wiki.openstreetmap.org/wiki/Overpass_API#Rules_of_usage">
     * wiki.openstreetmap.org/wiki/Overpass_API#Rules_of_usage</a> before choosing an endpoint
     * and a User-Agent that uniquely identifies your application.
     *
     * @param overpassEndpoint the Overpass API endpoint to query for roadway geometry
     * @param userAgent a value that uniquely identifies the calling application
     * @return a service with roadway-backed heading-slice validation enabled
     */
    public static TimValidationService withOverpassRoadGeometry(
            String overpassEndpoint,
            String userAgent) {
        return new TimValidationService(
                new OverpassRoadGeometryProvider(overpassEndpoint, userAgent));
    }

    /**
     * Creates a validation service with an injected roadway source.
     *
     * @param roadGeometryProvider provider used to retrieve nearby road geometry
     */
    public TimValidationService(RoadGeometryProvider roadGeometryProvider) {
        this(
                new BestPracticesValidator(Objects.requireNonNull(
                        roadGeometryProvider,
                        "roadGeometryProvider")),
                ValidationOptions.withRoadwayHeading());
    }

    private TimValidationService(
            BestPracticesValidator bestPracticesValidator,
            ValidationOptions defaultOptions) {
        this.uperToMessageFrameConverter = new UperToMessageFrameConverter();
        this.jerToMessageFrameConverter = new JerToMessageFrameConverter();
        this.j2735SchemaValidator = new TimJsonValidator();
        this.itwgSchemaValidator = new ItwgTimJsonValidator();
        this.itisContentValidator = new ItisJsonValidator();
        this.bestPracticesValidator = bestPracticesValidator;
        this.defaultOptions = defaultOptions;
    }

    /**
     * Validates a TIM message from UPER format.
     *
     * @param uperString the UPER encoded TIM message
     * @return ValidationResult containing validation status and details
     * @throws ValidationException if validation fails critically
     */
    public ValidationResult validateTim(String uperString) throws ValidationException {
        return validateTim(uperString, defaultOptions);
    }

    /**
     * Validates a TIM message from UPER format using per-call options.
     *
     * @param uperString the UPER encoded TIM message
     * @param options checks to perform for this validation
     * @return ValidationResult containing validation status and details
     * @throws ValidationException if validation fails critically
     */
    public ValidationResult validateTim(
            String uperString,
            ValidationOptions options) throws ValidationException {
        ValidationOptions validatedOptions = Objects.requireNonNull(options, "options");
        long validationStartNanos = System.nanoTime();
        ValidationResult result = new ValidationResult();
        result.setUperInput(uperString);
        result.setRoadwayHeadingValidationEnabled(validatedOptions.roadwayHeadingEnabled());

        try {
            String xerFormat = uperToMessageFrameConverter.convertUperToXer(uperString);
            result.setXerFormat(xerFormat);

            TravelerInformationMessageFrame timMessage = uperToMessageFrameConverter.deserialize(xerFormat);
            result.setTimMessage(timMessage);

            return validateTimMessage(result, timMessage, validatedOptions);
        } catch (Exception e) {
            throw buildValidationException(result, e);
        } finally {
            result.setValidationDurationMs(elapsedMillis(validationStartNanos));
        }
    }

    /**
     * Validates a TIM message from JER/JSON format.
     *
     * @param jerString the JER/JSON encoded TIM message
     * @return ValidationResult containing validation status and details
     * @throws ValidationException if validation fails critically
     */
    public ValidationResult validateTimJer(String jerString) throws ValidationException {
        return validateTimJer(jerString, defaultOptions);
    }

    /**
     * Validates a TIM message from JER/JSON format using per-call options.
     *
     * @param jerString the JER/JSON encoded TIM message
     * @param options checks to perform for this validation
     * @return ValidationResult containing validation status and details
     * @throws ValidationException if validation fails critically
     */
    public ValidationResult validateTimJer(
            String jerString,
            ValidationOptions options) throws ValidationException {
        ValidationOptions validatedOptions = Objects.requireNonNull(options, "options");
        long validationStartNanos = System.nanoTime();
        ValidationResult result = new ValidationResult();
        result.setJerInput(jerString);
        result.setRoadwayHeadingValidationEnabled(validatedOptions.roadwayHeadingEnabled());

        try {
            TravelerInformationMessageFrame timMessage = jerToMessageFrameConverter.deserialize(jerString);
            result.setTimMessage(timMessage);

            return validateTimMessage(result, timMessage, validatedOptions);
        } catch (Exception e) {
            throw buildValidationException(result, e);
        } finally {
            result.setValidationDurationMs(elapsedMillis(validationStartNanos));
        }
    }

    private ValidationResult validateTimMessage(
            ValidationResult result,
            TravelerInformationMessageFrame timMessage,
            ValidationOptions options) {
        try {
            j2735SchemaValidator.validate(timMessage);
            result.addValidationCheck("J2735 Schema Validation", true, "Message conforms to generated J2735 schema");
        } catch (ValidationException ex) {
            result.addValidationCheck("J2735 Schema Validation", false, ex.getMessage());
            addExceptionIssues(result, "J2735 Schema Validation", ex);
        }

        try {
            itwgSchemaValidator.validate(timMessage);
            result.addValidationCheck("ITWG Schema Validation", true, "Message conforms to ITWG TIM profile schema");
        } catch (ValidationException ex) {
            result.addValidationCheck("ITWG Schema Validation", false, ex.getMessage());
            addExceptionIssues(result, "ITWG Schema Validation", ex);
        }

        try {
            List<ValidationIssue> itisWarnings = itisContentValidator.validateAndCollectWarnings(timMessage);
            result.addIssues(itisWarnings);
            String itisDetails = itisWarnings.isEmpty()
                ? "ITIS content conforms to expected patterns"
                : "ITIS content validation completed with non-blocking warnings";
            result.addValidationCheck("ITIS Content Validation", true, itisDetails);
        } catch (ValidationException ex) {
            result.addValidationCheck("ITIS Content Validation", false, ex.getMessage());
            addExceptionIssues(result, "ITIS Content Validation", ex);
        }

        List<ValidationIssue> bestPracticesIssues = List.of();
        try {
            bestPracticesIssues = bestPracticesValidator.validate(
                timMessage,
                options);
        } catch (ValidationException ex) {
            result.addValidationCheck("Best Practices Validation", false, ex.getMessage());
            addExceptionIssues(result, "Best Practices Validation", ex);
        }

        result.addIssues(bestPracticesIssues);
        List<String> bestPracticesErrors = bestPracticesIssues.stream()
            .filter(issue -> issue.severity() == ValidationSeverity.ERROR)
            .map(ValidationIssue::message)
            .toList();

        if (!bestPracticesErrors.isEmpty()) {
            result.addValidationCheck("Best Practices", false,
                String.join("; ", bestPracticesErrors));
        } else if (bestPracticesIssues.isEmpty()) {
            result.addValidationCheck("Best Practices", true,
                "All best practices checks passed");
        } else {
            result.addValidationCheck(
                "Best Practices",
                true,
                "Best practices validation completed with non-blocking warnings");
        }

        return result;
    }

    private long elapsedMillis(long startNanos) {
        long elapsedNanos = System.nanoTime() - startNanos;
        return elapsedNanos == 0 ? 0 : (elapsedNanos + 999_999) / 1_000_000;
    }

    private ValidationException buildValidationException(ValidationResult result, Exception e) {
        if (result.getValidationChecks().isEmpty()) {
            result.addValidationCheck("Validation Pipeline", false, e.getMessage());
            result.addError("Validation Pipeline", e.getMessage(), null);
        } else if (result.getErrors().isEmpty()
                && !(e instanceof ValidationException validationException
                    && !validationException.getIssues().isEmpty())) {
            result.addError("Validation Pipeline", e.getMessage(), null);
        }

        result.setErrorMessage(e.getMessage());
        return new ValidationException("TIM validation failed: " + e.getMessage(), e, result);
    }

    private void addExceptionIssues(ValidationResult result, String checkName, Exception ex) {
        if (ex instanceof ValidationException validationException && !validationException.getIssues().isEmpty()) {
            result.addIssues(validationException.getIssues());
            return;
        }

        result.addError(checkName, ex.getMessage(), null);
    }
}
