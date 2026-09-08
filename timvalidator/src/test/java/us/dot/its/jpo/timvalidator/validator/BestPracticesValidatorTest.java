package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import us.dot.its.jpo.asn.j2735.r2024.Common.DYear;
import us.dot.its.jpo.asn.j2735.r2024.ITIS.ITIScodes;
import us.dot.its.jpo.asn.j2735.r2024.ITIS.ITIScodesAndText;
import us.dot.its.jpo.asn.j2735.r2024.ITIS.ITIScodesAndTextSequence;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.MinutesDuration;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrame;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrameList;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerInformation;
import us.dot.its.jpo.timvalidator.pojo.ValidationIssue;
import us.dot.its.jpo.timvalidator.pojo.ValidationSeverity;

class BestPracticesValidatorTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2025-06-01T00:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void validate_futureStartYearProducesWarning() throws Exception {
        int futureYear = 2026;

        List<ValidationIssue> issues = validate(dataFrame(futureYear, 60));

        assertEquals(1, issues.size());
        ValidationIssue issue = issues.getFirst();
        assertEquals(ValidationSeverity.WARNING, issue.severity());
        assertEquals("Best Practices", issue.checkName());
        assertEquals("/value/TravelerInformation/dataFrames/0/startYear", issue.path());
        assertTrue(issue.message().contains("startYear " + futureYear));
        assertTrue(issue.message().contains("later than the current UTC year"));
    }

    @Test
    void validate_currentAndPastStartYearsWithDefiniteDurationsDoNotWarn() throws Exception {
        assertTrue(validate(
                dataFrame(2025, 60),
                dataFrame(2024, 120)).isEmpty());
    }

    @Test
    void validate_nonWorkerIndefiniteDurationProducesWarning() throws Exception {
        List<ValidationIssue> issues = validate(dataFrame(2025, 32_000));

        assertEquals(1, issues.size());
        ValidationIssue issue = issues.getFirst();
        assertEquals(ValidationSeverity.WARNING, issue.severity());
        assertEquals("Best Practices", issue.checkName());
        assertEquals("/value/TravelerInformation/dataFrames/0/durationTime", issue.path());
        assertTrue(issue.message().contains("indefinite end time which is not recommended"));
    }

    @Test
    void validate_workerIndefiniteDurationProducesError() throws Exception {
        TravelerDataFrame dataFrame = dataFrame(2025, 32_000);
        dataFrame.setContent(advisoryContent(1_025, 6_952));

        List<ValidationIssue> issues = validate(dataFrame);

        assertEquals(1, issues.size());
        ValidationIssue issue = issues.getFirst();
        assertEquals(ValidationSeverity.ERROR, issue.severity());
        assertEquals("Best Practices", issue.checkName());
        assertEquals("/value/TravelerInformation/dataFrames/0/durationTime", issue.path());
        assertTrue(issue.message().contains("Worker-related data frame 0"));
        assertTrue(issue.message().contains("worker TIMs must use a limited time window"));
    }

    @Test
    void validate_workerFiniteDurationDoesNotProduceIssue() throws Exception {
        TravelerDataFrame dataFrame = dataFrame(2025, 60);
        dataFrame.setContent(advisoryContent(6_952));

        assertTrue(validate(dataFrame).isEmpty());
    }

    @Test
    void validate_normalDurationDoesNotProduceWarning() throws Exception {
        assertTrue(validate(dataFrame(2025, 60)).isEmpty());
    }

    private static List<ValidationIssue> validate(TravelerDataFrame... dataFrames)
            throws Exception {
        TravelerDataFrameList dataFrameList = new TravelerDataFrameList();
        for (TravelerDataFrame dataFrame : dataFrames) {
            dataFrameList.add(dataFrame);
        }
        TravelerInformation tim = new TravelerInformation();
        tim.setDataFrames(dataFrameList);
        return new BestPracticesValidator(CLOCK).validate(tim);
    }

    private static TravelerDataFrame dataFrame(int startYear, long durationMinutes) {
        TravelerDataFrame dataFrame = new TravelerDataFrame();
        dataFrame.setStartYear(new DYear(startYear));
        dataFrame.setDurationTime(new MinutesDuration(durationMinutes));
        return dataFrame;
    }

    private static TravelerDataFrame.ContentChoice advisoryContent(int... itisCodes) {
        TravelerDataFrame.ContentChoice content = new TravelerDataFrame.ContentChoice();
        ITIScodesAndText advisory = new ITIScodesAndText();
        for (int itisCode : itisCodes) {
            ITIScodesAndTextSequence sequence = new ITIScodesAndTextSequence();
            ITIScodesAndTextSequence.ItemChoice item = new ITIScodesAndTextSequence.ItemChoice();
            item.setItis(new ITIScodes(itisCode));
            sequence.setItem(item);
            advisory.add(sequence);
        }
        content.setAdvisory(advisory);
        return content;
    }
}
