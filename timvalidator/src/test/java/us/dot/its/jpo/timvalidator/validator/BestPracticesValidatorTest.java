package us.dot.its.jpo.timvalidator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Year;
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

    @Test
    void validate_futureStartYearProducesWarning() throws Exception {
        int futureYear = Year.now(ZoneOffset.UTC).getValue() + 1;

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
        int currentYear = Year.now(ZoneOffset.UTC).getValue();

        assertTrue(validate(
                dataFrame(currentYear, 60),
                dataFrame(currentYear - 1, 120)).isEmpty());
    }

    @Test
    void validate_indefiniteDurationProducesWarning() throws Exception {
        int currentYear = Year.now(ZoneOffset.UTC).getValue();

        List<ValidationIssue> issues = validate(dataFrame(currentYear, 32_000));

        assertEquals(1, issues.size());
        ValidationIssue issue = issues.getFirst();
        assertEquals(ValidationSeverity.WARNING, issue.severity());
        assertEquals("Best Practices", issue.checkName());
        assertEquals("/value/TravelerInformation/dataFrames/0/durationTime", issue.path());
        assertTrue(issue.message().contains("indefinite end time which is not recommended"));
    }

    private static List<ValidationIssue> validate(TravelerDataFrame... dataFrames)
            throws Exception {
        TravelerDataFrameList dataFrameList = new TravelerDataFrameList();
        for (TravelerDataFrame dataFrame : dataFrames) {
            dataFrameList.add(dataFrame);
        }
        TravelerInformation tim = new TravelerInformation();
        tim.setDataFrames(dataFrameList);
        return new BestPracticesValidator().validate(tim);
    }

    private static TravelerDataFrame dataFrame(int startYear, long durationMinutes) {
        TravelerDataFrame dataFrame = new TravelerDataFrame();
        dataFrame.setStartYear(new DYear(startYear));
        dataFrame.setDurationTime(new MinutesDuration(durationMinutes));
        return dataFrame;
    }
}
