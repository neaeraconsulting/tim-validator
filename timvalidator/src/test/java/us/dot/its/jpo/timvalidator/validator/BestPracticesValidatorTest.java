package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import us.dot.its.jpo.asn.j2735.r2024.Common.DYear;
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
    void validate_indefiniteDurationProducesWarning() throws Exception {
        List<ValidationIssue> issues = validate(dataFrame(2025, 32_000));

        assertEquals(1, issues.size());
        ValidationIssue issue = issues.getFirst();
        assertEquals(ValidationSeverity.WARNING, issue.severity());
        assertEquals("Best Practices", issue.checkName());
        assertEquals("/value/TravelerInformation/dataFrames/0/durationTime", issue.path());
        assertTrue(issue.message().contains("indefinite end time which is not recommended"));
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
}
