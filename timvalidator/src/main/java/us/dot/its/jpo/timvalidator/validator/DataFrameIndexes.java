package us.dot.its.jpo.timvalidator.validator;

import java.util.Locale;

/** Identifies a geographical region by its indexes within a TIM data-frame collection. */
public record DataFrameIndexes(int dataFrameIndex, int regionIndex) {

  public String regionPath() {
    return String.format(Locale.ROOT,
        "/value/TravelerInformation/dataFrames/%d/regions/%d",
        dataFrameIndex,
        regionIndex);
  }

}
