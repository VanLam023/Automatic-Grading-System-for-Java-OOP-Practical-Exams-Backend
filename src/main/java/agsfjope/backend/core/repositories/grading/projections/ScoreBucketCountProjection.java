package agsfjope.backend.core.repositories.grading.projections;

/**
 * Projection for aggregated score-distribution buckets.
 */
public interface ScoreBucketCountProjection {
    String getBucketLabel();
    Long getBucketCount();
}