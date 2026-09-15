package us.dot.its.jpo.timvalidator.validator;

import java.util.Locale;

/** Identifies a geographical region by its indexes within a TIM data-frame collection. 
 * @param dataFrameIndex the index of the data frame within the TIM data-frame collection
 * @param regionIndex the index of the region within the specified data frame
*/
public record DataFrameIndexes(int dataFrameIndex, int regionIndex) {

  /**
   * Returns the JSON path to the region within the TIM data-frame collection.
   * @return the JSON path as a string
   */
  public String regionPath() {
    return String.format(Locale.ROOT,
        "/value/TravelerInformation/dataFrames/%d/regions/%d",
        dataFrameIndex,
        regionIndex);
  }

}
