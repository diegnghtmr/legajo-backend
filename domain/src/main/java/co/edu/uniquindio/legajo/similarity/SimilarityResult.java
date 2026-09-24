package co.edu.uniquindio.legajo.similarity;

import org.jspecify.annotations.Nullable;

/**
 * Common outcome envelope every {@link SimilarityAlgorithm} returns from
 * {@code compute()}: a score normalized to the closed interval [0,1], the
 * algorithm's raw value before normalization (nullable — {@code null} only in the
 * documented degenerate cases such as the TF-IDF null vector), the elapsed time measured
 * inside {@code compute()} in nanoseconds, and whether this result is one of the
 * documented degenerate cases.
 *
 * <p>{@code normalizedScore} tolerates the ±1e-9 floating-point overshoot explicitly
 * allowed for identical inputs (e.g. cosine of a vector with itself landing at
 * {@code 1.0000000001}) instead of rejecting it as out of range.
 */
public record SimilarityResult(double normalizedScore, @Nullable Double rawValue, long computedNanos,
        boolean degenerate) {

    private static final double RANGE_TOLERANCE = 1e-9;

    public SimilarityResult {
        // NumericGuards.isOutOfRange rejects a non-finite normalizedScore explicitly: a bare
        // range comparison (normalizedScore < min || normalizedScore > max) is false for NaN
        // on both sides, so it would otherwise let a NaN score through silently.
        if (NumericGuards.isOutOfRange(normalizedScore, 0.0, 1.0, RANGE_TOLERANCE)) {
            throw new IllegalArgumentException(
                    "normalizedScore must be within [0,1] (±%.0e tolerance), was %s"
                            .formatted(RANGE_TOLERANCE, normalizedScore));
        }
        if (computedNanos < 0) {
            throw new IllegalArgumentException("computedNanos must not be negative, was " + computedNanos);
        }
    }

    /** Convenience constructor for the common, non-degenerate case (the fixed default). */
    public SimilarityResult(double normalizedScore, @Nullable Double rawValue, long computedNanos) {
        this(normalizedScore, rawValue, computedNanos, false);
    }
}
