package us.dot.its.jpo.timvalidator.validator;

/** Identifies a geographical region by its indexes within a TIM data-frame collection. */
public record DataFrameIndexes(int dataFrameIndex, int regionIndex) {
}
