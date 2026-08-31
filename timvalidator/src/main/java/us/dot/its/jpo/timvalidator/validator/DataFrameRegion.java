package us.dot.its.jpo.timvalidator.validator;

import java.util.stream.Stream;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.GeographicalPath;
import us.dot.its.jpo.asn.j2735.r2024.TravelerInformation.TravelerDataFrameList;

/**
 * Utility to temporarily hold an indexed region (GeographicalPath)
 * @param indexes The Indexes
 * @param path The Region
 */
record DataFrameRegion(DataFrameIndexes indexes, GeographicalPath path) {

  /**
   * Utility method to iterate over data frames and regions
   * @param travelerDataFrameList Input list of data frames
   * @return stream of indexed regions
   */
  public static Stream<DataFrameRegion> regions(TravelerDataFrameList travelerDataFrameList) {
    Stream.Builder<DataFrameRegion> streamBuilder = Stream.builder();

    if (travelerDataFrameList == null || travelerDataFrameList.isEmpty())
      return streamBuilder.build();

    for (int dataFrameIndex = 0; dataFrameIndex < travelerDataFrameList.size(); ++dataFrameIndex) {
      var dataFrame = travelerDataFrameList.get(dataFrameIndex);

      if (dataFrame == null || dataFrame.getRegions() == null)
        continue;

      var regions = dataFrame.getRegions();
      for (int regionIndex = 0; regionIndex < regions.size(); ++regionIndex) {
        final var region = regions.get(regionIndex);

        if (region == null)
          continue;

        final var indexes = new DataFrameIndexes(dataFrameIndex, regionIndex);
        final DataFrameRegion dataFrameRegion = new DataFrameRegion(indexes, region);
        streamBuilder.add(dataFrameRegion);
      }
    }
    return streamBuilder.build();
  }

}
