package us.dot.its.jpo.timvalidator.validator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void validate_fullTimMessageWithSpeedLimitPathPattern_passesValidation() {
        assertDoesNotThrow(() -> validator.validate(buildTimMessage(List.of(268, 12599))));
    }

    @Test
    void validate_fullTimMessageWithPolygonOnlyPattern_passesValidation() {
        TravelerInformationMessageFrame messageFrame = buildTimMessage(List.of(2577, 11581, 8739));

        assertDoesNotThrow(() -> validator.validate(messageFrame));
    }

    @Test
    void validate_fullTimMessageWithWeightUnitPattern_passesValidation() {
        TravelerInformationMessageFrame messageFrame = buildTimMessage(List.of(2577, 11581, 8740));

        assertDoesNotThrow(() -> validator.validate(messageFrame));
    }

    @Test
    void validate_fullTimMessageWithCircleOnlyPattern_passesValidation() {
        TravelerInformationMessageFrame messageFrame = buildTimMessage(List.of(268, 12302, 8720, 13569));

        assertDoesNotThrow(() -> validator.validate(messageFrame));
    }

    @Test
    void validate_fullTimMessageWithClosedToTrafficPattern_passesValidation() {
        TravelerInformationMessageFrame messageFrame = buildTimMessage(List.of(769, 9478, 7747));

        assertDoesNotThrow(() -> validator.validate(messageFrame));
    }

    @Test
    void validateAndCollectWarnings_fullTimMessageWithNumericItisCodes_returnsNoWarnings() throws Exception {
        List<ValidationIssue> warnings = validator.validateAndCollectWarnings(buildTimMessage(List.of(268, 12599)));

        assertTrue(warnings.isEmpty());
    }

    @Test
    void validateAndCollectWarnings_textOnlyAdvisory_returnsWarnings() throws Exception {
        TravelerInformationMessageFrame messageFrame = buildTimMessageWithContent(buildTextAdvisoryContent());

        List<ValidationIssue> warnings = validator.validateAndCollectWarnings(messageFrame);

        assertTrue(warnings.stream().anyMatch(warning -> warning.message().contains("contains no numeric ITIS codes")
            && warning.path().endsWith("/content/advisory")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.message().contains("No data frame contained numeric advisory ITIS codes")
            && warning.path().equals("/value/TravelerInformation/dataFrames")));
    }

    @Test
    void validateAndCollectWarnings_emptyAdvisory_returnsWarnings() throws Exception {
        TravelerInformationMessageFrame messageFrame = buildTimMessageWithContent(buildAdvisoryContent(List.of()));

        List<ValidationIssue> warnings = validator.validateAndCollectWarnings(messageFrame);

        assertTrue(warnings.stream().anyMatch(warning -> warning.message().contains("empty advisory content")
            && warning.path().endsWith("/content/advisory")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.message().contains("No data frame contained numeric advisory ITIS codes")
            && warning.path().equals("/value/TravelerInformation/dataFrames")));
    }

    @Test
    void validateAndCollectWarnings_nonAdvisoryContent_returnsWarnings() throws Exception {
        TravelerInformationMessageFrame messageFrame = buildTimMessageWithContent(buildGenericSignContent());

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

        assertTrue(ex.getMessage().contains("/itis"));
        assertTrue(ex.getIssues().stream().anyMatch(issue -> issue.path().equals("/itis")));
    }

    @Test
    void validate_fullTimMessageWithTruckCheckpointSpeedLimitPattern_failsValidation() {
        TravelerInformationMessageFrame messageFrame = buildTimMessage(List.of(6937, 12599));

        ValidationException ex = assertThrows(ValidationException.class, () -> validator.validate(messageFrame));

        assertTrue(ex.getMessage().contains("/itis"));
    }

    private static TravelerInformationMessageFrame buildTimMessage(List<Integer> itisCodes) {
        return buildTimMessage(buildAdvisoryContent(itisCodes));
    }

    private static TravelerInformationMessageFrame buildTimMessageWithContent(TravelerDataFrame.ContentChoice content) {
        return buildTimMessage(content);
    }

    private static TravelerInformationMessageFrame buildTimMessage(TravelerDataFrame.ContentChoice content) {
        TravelerInformationMessageFrame messageFrame = new TravelerInformationMessageFrame();
        TravelerInformation travelerInformation = new TravelerInformation();
        TravelerDataFrameList dataFrames = new TravelerDataFrameList();
        TravelerDataFrame dataFrame = new TravelerDataFrame();

        travelerInformation.setMsgCnt(new MsgCount(1));
        dataFrame.setContent(content);

        dataFrames.add(dataFrame);
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
