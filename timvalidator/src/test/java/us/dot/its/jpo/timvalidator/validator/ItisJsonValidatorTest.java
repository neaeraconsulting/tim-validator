package us.dot.its.jpo.timvalidator.validator;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import us.dot.its.jpo.asn.j2735.r2024.Common.MsgCount;
import us.dot.its.jpo.asn.j2735.r2024.ITIS.ITIScodes;
import us.dot.its.jpo.asn.j2735.r2024.ITIS.ITIScodesAndText;
import us.dot.its.jpo.asn.j2735.r2024.ITIS.ITIScodesAndTextSequence;
import us.dot.its.jpo.asn.j2735.r2024.ITIS.ITIStext;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GenericSignage;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrameList;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformationMessageFrame;
import us.dot.its.jpo.timvalidator.exception.ValidationException;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;

class ItisJsonValidatorTest {

    private ItisJsonValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ItisJsonValidator();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("supportedItisPatterns")
    void validate_fullTimMessageWithSupportedItisPattern_passesValidation(String patternName, List<Integer> itisCodes) {
        assertDoesNotThrow(() -> validator.validate(buildTimMessage(itisCodes)), patternName);
    }

    @Test
    void validateAndCollectWarnings_fullTimMessageWithNumericItisCodes_returnsNoWarnings() throws ValidationException {
        List<ValidationIssue> warnings = validator.validateAndCollectWarnings(buildTimMessage(List.of(268, 12599, 8720)));

        assertTrue(warnings.isEmpty());
    }

    @Test
    void validateAndCollectWarnings_textOnlyAdvisory_returnsWarnings() throws ValidationException {
        TravelerInformationMessageFrame messageFrame = buildTimMessage(buildTextAdvisoryContent());

        List<ValidationIssue> warnings = validator.validateAndCollectWarnings(messageFrame);

        assertTrue(warnings.stream().anyMatch(warning -> warning.message().contains("contains no numeric ITIS codes")
            && warning.path().endsWith("/content/advisory")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.message().contains("No data frame contained numeric advisory ITIS codes")
            && warning.path().equals("/value/TravelerInformation/dataFrames")));
    }

    @Test
    void validateAndCollectWarnings_emptyAdvisory_returnsWarnings() throws ValidationException {
        TravelerInformationMessageFrame messageFrame = buildTimMessage(buildAdvisoryContent(List.of()));

        List<ValidationIssue> warnings = validator.validateAndCollectWarnings(messageFrame);

        assertTrue(warnings.stream().anyMatch(warning -> warning.message().contains("empty advisory content")
            && warning.path().endsWith("/content/advisory")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.message().contains("No data frame contained numeric advisory ITIS codes")
            && warning.path().equals("/value/TravelerInformation/dataFrames")));
    }

    @Test
    void validateAndCollectWarnings_nonAdvisoryContent_returnsWarnings() throws ValidationException {
        TravelerInformationMessageFrame messageFrame = buildTimMessage(buildGenericSignContent());

        List<ValidationIssue> warnings = validator.validateAndCollectWarnings(messageFrame);

        assertTrue(warnings.stream().anyMatch(warning -> warning.message().contains("uses non-advisory content")
            && warning.path().endsWith("/content")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.message().contains("No data frame contained numeric advisory ITIS codes")
            && warning.path().equals("/value/TravelerInformation/dataFrames")));
    }

    @Test
    void validate_fullTimMessageWithUnknownItisPattern_failsValidation() {
        TravelerInformationMessageFrame messageFrame = buildTimMessage(List.of(268, 12302, 9999, 13569));

        ValidationException ex = assertThrows(ValidationException.class, () -> validator.validate(messageFrame));

        assertEquals(
            "ITIS sequence does not match any supported pattern: [268, 12302, 9999, 13569].",
            ex.getMessage());
        assertEquals(1, ex.getIssues().size());
        assertEquals(
            "ITIS sequence does not match any supported pattern: [268, 12302, 9999, 13569].",
            ex.getIssues().getFirst().message());
        assertEquals(
            "/value/TravelerInformation/dataFrames/0/content/advisory",
            ex.getIssues().getFirst().path());
    }

    @Test
    void validate_fullTimMessageWithTruckCheckpointSpeedLimitPattern_failsValidation() {
        TravelerInformationMessageFrame messageFrame = buildTimMessage(List.of(6937, 12599));

        ValidationException ex = assertThrows(ValidationException.class, () -> validator.validate(messageFrame));

        assertEquals("ITIS sequence does not match any supported pattern: [6937, 12599].", ex.getMessage());
        assertEquals(1, ex.getIssues().size());
        assertEquals(
            "/value/TravelerInformation/dataFrames/0/content/advisory",
            ex.getIssues().getFirst().path());
    }

    @Test
    void validate_multipleDataFramesWithInvalidFirstFrame_reportsEveryInvalidFrame() {
        TravelerInformationMessageFrame messageFrame = buildTimMessageWithContents(
            buildAdvisoryContent(List.of(268, 12302, 9999, 13569)),
            buildAdvisoryContent(List.of(531, 13569)),
            buildAdvisoryContent(List.of(6937, 12599)));

        ValidationException ex = assertThrows(ValidationException.class, () -> validator.validate(messageFrame));

        assertEquals(2, ex.getIssues().size());
        assertEquals(
            List.of(
                "/value/TravelerInformation/dataFrames/0/content/advisory",
                "/value/TravelerInformation/dataFrames/2/content/advisory"),
            ex.getIssues().stream().map(ValidationIssue::path).toList());
        assertTrue(ex.getMessage().contains("[268, 12302, 9999, 13569]"));
        assertTrue(ex.getMessage().contains("[6937, 12599]"));
    }

    private static TravelerInformationMessageFrame buildTimMessage(List<Integer> itisCodes) {
        return buildTimMessage(buildAdvisoryContent(itisCodes));
    }

    private static Stream<Arguments> supportedItisPatterns() {
        return Stream.of(
            Arguments.of("closed-to-traffic-local-traffic-only", List.of(769, 9478, 7747)),
            Arguments.of("incident-ahead", List.of(531, 13569)),
            Arguments.of("accident-ahead", List.of(513, 13569)),
            Arguments.of("left-lane-blocked-ahead", List.of(8195, 776)),
            Arguments.of("left-lanes-blocked-ahead", List.of(13580, 12547, 13588, 776)),
            Arguments.of("right-lane-blocked-ahead", List.of(8196, 776)),
            Arguments.of("right-lanes-blocked-ahead", List.of(13579, 12547, 13588, 776)),
            Arguments.of("wind-may-exceed-mph", List.of(5131, 7759, 12599, 8720)),
            Arguments.of("strong-winds-high-profile-hazardous", List.of(5127, 9233, 6405)),
            Arguments.of("caution-wind-type", List.of(12330, 5127)),
            Arguments.of("high-profile-vehicle-restrictions", List.of(5127, 2563, 2569, 7682, 2577, 11581, 8739)),
            Arguments.of("truck-gross-weight-limit-pounds", List.of(2563, 2577, 11581, 8739)),
            Arguments.of("truck-gross-weight-limit-tons", List.of(2563, 2577, 11581, 8740)),
            Arguments.of("truck-length-limit-feet", List.of(2563, 2575, 12599, 8710)),
            Arguments.of("wrong-way", List.of(12310)),
            Arguments.of("danger-ahead-wrong-way-confirmed", List.of(6917, 13569, 1793, 2822)),
            Arguments.of("hurricane-evacuation-route", List.of(5122, 8468)),
            Arguments.of("visibility-reduced", List.of(5383)),
            Arguments.of("dense-fog-ahead", List.of(5377, 13569)),
            Arguments.of("be-prepared-to-stop", List.of(7201)),
            Arguments.of("road-construction-be-prepared-to-stop", List.of(1025, 7201)),
            Arguments.of("traffic-congestion-ahead", List.of(263, 13569)),
            Arguments.of("road-construction-traffic-congestion-ahead", List.of(1025, 263, 7201)),
            Arguments.of("left-turn-advisory-speed-limit", List.of(13594, 7712, 8026, 268, 12599, 8720)),
            Arguments.of("hairpin-right-advisory-speed-limit", List.of(13617, 7712, 8026, 268, 12599, 8720)),
            Arguments.of("winding-road-to-left-advisory-speed-limit", List.of(13641, 7712, 8026, 268, 12599, 8720)),
            Arguments.of("debris-on-roadway", List.of(1284)),
            Arguments.of("slippery-wet-pavement", List.of(5897, 5895)),
            Arguments.of("surface-water-hazard", List.of(5893)),
            Arguments.of("ice", List.of(5906)),
            Arguments.of("severe-weather", List.of(4865)),
            Arguments.of("blowing-snow", List.of(5385)),
            Arguments.of("flooding-ahead", List.of(1301, 13569)),
            Arguments.of("flooding-road-closed-ahead", List.of(1301, 11793, 771)),
            Arguments.of("emergency-vehicles-on-roadway", List.of(1796)),
            Arguments.of("emergency-vehicles-at-scene", List.of(6925)),
            Arguments.of("road-construction", List.of(1025)),
            Arguments.of("road-construction-ahead", List.of(1025, 13569)),
            Arguments.of("left-lane-closed-ahead", List.of(8195, 771)),
            Arguments.of("left-lanes-closed-ahead", List.of(13580, 12547, 13588, 771)),
            Arguments.of("right-lane-closed-ahead", List.of(8196, 771)),
            Arguments.of("right-lanes-closed-ahead", List.of(13579, 12547, 13588, 771)),
            Arguments.of("center-lane-closed-ahead", List.of(8197, 771)),
            Arguments.of("closed-to-traffic", List.of(769)),
            Arguments.of("closed-for-the-season", List.of(774)),
            Arguments.of("look-out-for-workers", List.of(6952)),
            Arguments.of("do-not-enter", List.of(12314)),
            Arguments.of("stop", List.of(12294)),
            Arguments.of("yield", List.of(12295)),
            Arguments.of("advisory-speed-limit-kph", List.of(7712, 268, 12599, 8721)),
            Arguments.of("regulatory-speed-limit-mph", List.of(268, 12599, 8720)),
            Arguments.of("speed-limit-reduced-ahead", List.of(268, 12302, 12599, 8720, 13569)),
            Arguments.of("truck-bus-speed-limit", List.of(268, 9227, 9228, 12589, 8720)),
            Arguments.of("school-zone-speed-limit-ahead", List.of(4124, 268, 12599, 8720, 13569)),
            Arguments.of("combined-minimum-speed-limit", List.of(268, 12303, 12599, 8720, 12304, 11581, 8720)),
            Arguments.of("stop-ahead-toll-plaza", List.of(12294, 13569, 8217)),
            Arguments.of("exit-for-toll-plaza-ahead", List.of(11794, 7721, 8217, 13569)),
            Arguments.of("runaway-vehicle-escape-ramp", List.of(9256, 8234, 11531, 8712, 13569, 7989)),
            Arguments.of("truck-parking-ahead", List.of(9227, 4120, 13569)),
            Arguments.of("gasoline-ahead", List.of(11016, 13569)),
            Arguments.of("diesel-ahead", List.of(11015, 13569)),
            Arguments.of("test-brakes-parking-area-ahead", List.of(7174, 11791, 13569)),
            Arguments.of("snow-chains-required-parking-area-ahead", List.of(6148, 11791, 13569)),
            Arguments.of("snow-tires-or-chains-required-all-vehicles", List.of(6156, 9217)),
            Arguments.of("weigh-station-mile-ahead", List.of(11792, 12545, 8711, 13569)),
            Arguments.of("weigh-station-open-next-right", List.of(11792, 892, 13582, 13579))
        );
    }

    private static TravelerInformationMessageFrame buildTimMessage(TravelerDataFrame.ContentChoice content) {
        return buildTimMessageWithContents(content);
    }

    private static TravelerInformationMessageFrame buildTimMessageWithContents(
            TravelerDataFrame.ContentChoice... contents) {
        TravelerInformationMessageFrame messageFrame = new TravelerInformationMessageFrame();
        TravelerInformation travelerInformation = new TravelerInformation();
        TravelerDataFrameList dataFrames = new TravelerDataFrameList();

        travelerInformation.setMsgCnt(new MsgCount(1));
        for (TravelerDataFrame.ContentChoice content : contents) {
            TravelerDataFrame dataFrame = new TravelerDataFrame();
            dataFrame.setContent(content);
            dataFrames.add(dataFrame);
        }
        travelerInformation.setDataFrames(dataFrames);
        messageFrame.setValue(travelerInformation);
        return messageFrame;
    }

    private static TravelerDataFrame.ContentChoice buildAdvisoryContent(List<Integer> itisCodes) {
        TravelerDataFrame.ContentChoice content = new TravelerDataFrame.ContentChoice();
        ITIScodesAndText advisory = new ITIScodesAndText();

        for (Integer code : itisCodes) {
            ITIScodesAndTextSequence sequence = new ITIScodesAndTextSequence();
            ITIScodesAndTextSequence.ItemChoice item = new ITIScodesAndTextSequence.ItemChoice();
            item.setItis(new ITIScodes(code));
            sequence.setItem(item);
            advisory.add(sequence);
        }

        content.setAdvisory(advisory);
        return content;
    }

    private static TravelerDataFrame.ContentChoice buildTextAdvisoryContent() {
        TravelerDataFrame.ContentChoice content = new TravelerDataFrame.ContentChoice();
        ITIScodesAndText advisory = new ITIScodesAndText();
        ITIScodesAndTextSequence sequence = new ITIScodesAndTextSequence();
        ITIScodesAndTextSequence.ItemChoice item = new ITIScodesAndTextSequence.ItemChoice();

        item.setText(new ITIStext("text-only advisory"));
        sequence.setItem(item);
        advisory.add(sequence);
        content.setAdvisory(advisory);
        return content;
    }

    private static TravelerDataFrame.ContentChoice buildGenericSignContent() {
        TravelerDataFrame.ContentChoice content = new TravelerDataFrame.ContentChoice();
        content.setGenericSign(new GenericSignage());
        return content;
    }
}
