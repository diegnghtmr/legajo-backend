package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Validated invariants of {@link EmbeddingApiTrace} (TRD §6.3's embedding-api trace row):
 * {@code vectorA}/{@code vectorB} must match {@code dimension}; the excerpts must be exactly
 * the first {@code min(8, dimension)} components of the corresponding full vector;
 * {@code sumSquaredDiff} must equal the hand-computed sum of squared differences of the two
 * vectors; {@code distance} must equal {@code sqrt(sumSquaredDiff)}; {@code normalizedScore}
 * must equal {@code clamp(1 - distance/sqrt(2), 0, 1)}.
 */
class EmbeddingApiTraceTest {

    private static final List<Double> VECTOR_A = List.of(0.6, 0.8, 0.0);
    private static final List<Double> VECTOR_B = List.of(0.0, 0.6, 0.8);
    private static final double SUM_SQUARED_DIFF = 1.04;
    private static final double DISTANCE = 1.019803902718557;
    private static final double NORMALIZED_SCORE = 0.2788897449072022;

    private static EmbeddingApiTrace validTrace() {
        return new EmbeddingApiTrace("embedding-api", "api", "gemini-embedding-2-preview", 3, VECTOR_A, VECTOR_B,
                VECTOR_A, VECTOR_B, 1.0, 1.0, SUM_SQUARED_DIFF, DISTANCE, NORMALIZED_SCORE, "cached");
    }

    @Test
    void acceptsAConsistentTrace() {
        validTrace();
    }

    @Test
    void rejectsANullAlgorithmId() {
        assertThatNullPointerException().isThrownBy(() -> new EmbeddingApiTrace(null, "api",
                "gemini-embedding-2-preview", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, SUM_SQUARED_DIFF,
                DISTANCE, NORMALIZED_SCORE, "cached"));
    }

    @Test
    void rejectsABlankProviderStatus() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingApiTrace("embedding-api", "api",
                "gemini-embedding-2-preview", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, SUM_SQUARED_DIFF,
                DISTANCE, NORMALIZED_SCORE, "  "));
    }

    @Test
    void rejectsAVectorASizeMismatchWithDimension() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingApiTrace("embedding-api", "api",
                "gemini-embedding-2-preview", 4, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, SUM_SQUARED_DIFF,
                DISTANCE, NORMALIZED_SCORE, "cached"));
    }

    @Test
    void rejectsAnExcerptThatIsNotAPrefixOfTheFullVector() {
        List<Double> wrongExcerpt = List.of(9.0, 9.0, 9.0);
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingApiTrace("embedding-api", "api",
                "gemini-embedding-2-preview", 3, wrongExcerpt, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0,
                SUM_SQUARED_DIFF, DISTANCE, NORMALIZED_SCORE, "cached"));
    }

    @Test
    void rejectsASumSquaredDiffThatDoesNotMatchTheVectors() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingApiTrace("embedding-api", "api",
                "gemini-embedding-2-preview", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, 99.0, DISTANCE,
                NORMALIZED_SCORE, "cached"));
    }

    @Test
    void rejectsADistanceThatDiffersFromTheSquareRootOfSumSquaredDiff() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingApiTrace("embedding-api", "api",
                "gemini-embedding-2-preview", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, SUM_SQUARED_DIFF,
                99.0, NORMALIZED_SCORE, "cached"));
    }

    @Test
    void rejectsANegativeDistance() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingApiTrace("embedding-api", "api",
                "gemini-embedding-2-preview", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, SUM_SQUARED_DIFF,
                -1.0, NORMALIZED_SCORE, "cached"));
    }

    @Test
    void rejectsANormalizedScoreThatIsNotTheClampedMapping() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingApiTrace("embedding-api", "api",
                "gemini-embedding-2-preview", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, SUM_SQUARED_DIFF,
                DISTANCE, 0.99, "cached"));
    }

    @Test
    void rejectsANegativePreNormL2() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingApiTrace("embedding-api", "api",
                "gemini-embedding-2-preview", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, -1.0, 1.0, SUM_SQUARED_DIFF,
                DISTANCE, NORMALIZED_SCORE, "cached"));
    }

    @Test
    void aDistanceBeyondSqrt2ClampsTheNormalizedScoreToZero() {
        List<Double> opposite = List.of(-0.6, -0.8, 0.0);
        new EmbeddingApiTrace("embedding-api", "api", "gemini-embedding-2-preview", 3, VECTOR_A, opposite, VECTOR_A,
                opposite, 1.0, 1.0, 4.0, 2.0, 0.0, "cached");
    }

    // sumSquaredDiff and distance were already guarded by an explicit Double.isFinite check
    // before their tolerance comparison (unlike normalizedScore below); these characterize
    // that existing behavior ahead of migrating both checks onto the shared NumericGuards
    // helper, so a refactor cannot silently regress it.

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteSumSquaredDiff(double nonFinite) {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingApiTrace("embedding-api", "api",
                        "gemini-embedding-2-preview", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, nonFinite,
                        DISTANCE, NORMALIZED_SCORE, "cached"))
                .withMessageContaining("sumSquaredDiff");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteDistance(double nonFinite) {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingApiTrace("embedding-api", "api",
                        "gemini-embedding-2-preview", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0,
                        SUM_SQUARED_DIFF, nonFinite, NORMALIZED_SCORE, "cached"))
                .withMessageContaining("distance");
    }

    // R3-expected-side-rejection-unproved: isOutOfTolerance already rejects when the
    // *expected* (derived) side of a comparison is non-finite, not just the raw field value
    // passed in — but that path was only ever exercised indirectly (by passing a non-finite
    // field directly above). This proves the rejection when the field ITSELF is finite and
    // plausible, but the vectors it is checked against make the derived expected value
    // non-finite (a NaN component propagates through the squared-difference sum).

    @Test
    void rejectsWhenANaNVectorComponentMakesTheExpectedSumSquaredDiffNonFinite() {
        List<Double> nanVectorA = new ArrayList<>(VECTOR_A);
        nanVectorA.set(0, Double.NaN);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmbeddingApiTrace("embedding-api", "api", "gemini-embedding-2-preview", 3,
                        nanVectorA, VECTOR_B, nanVectorA, VECTOR_B, 1.0, 1.0, SUM_SQUARED_DIFF, DISTANCE,
                        NORMALIZED_SCORE, "cached"))
                .withMessageContaining("sumSquaredDiff");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteNormalizedScore(double nonFinite) {
        // Math.abs(NaN - expected) > tolerance is false, so the old guard let a NaN
        // normalizedScore through silently instead of failing closed.
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingApiTrace("embedding-api", "api",
                        "gemini-embedding-2-preview", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0,
                        SUM_SQUARED_DIFF, DISTANCE, nonFinite, "cached"))
                .withMessageContaining("normalizedScore");
    }
}
