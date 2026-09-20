package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link SimilarityResult} is the common outcome envelope for every algorithm in
 * {@code SimilarityAlgorithm} (TRD §6.3): a normalized score in [0,1], an optional raw
 * value, the nanoseconds measured inside {@code compute()}, and the {@code degenerate}
 * flag required by the contract.
 */
class SimilarityResultTest {

    @Test
    void threeArgConstructorDefaultsDegenerateToFalse() {
        SimilarityResult result = new SimilarityResult(0.75, 1.0, 1_000L);

        assertThat(result.degenerate()).isFalse();
    }

    @Test
    void fourArgConstructorHonorsExplicitDegenerateFlag() {
        SimilarityResult result = new SimilarityResult(1.0, null, 500L, true);

        assertThat(result.degenerate()).isTrue();
        assertThat(result.rawValue()).isNull();
    }

    @Test
    void rejectsNormalizedScoreBelowZeroBeyondTolerance() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SimilarityResult(-0.01, 0.0, 0L, false))
                .withMessageContaining("normalizedScore");
    }

    @Test
    void rejectsNormalizedScoreAboveOneBeyondTolerance() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SimilarityResult(1.01, 0.0, 0L, false))
                .withMessageContaining("normalizedScore");
    }

    @Test
    void toleratesFloatingPointOvershootWithinOneNanoUnit() {
        SimilarityResult belowZero = new SimilarityResult(-1e-10, 0.0, 0L, false);
        SimilarityResult aboveOne = new SimilarityResult(1.0 + 1e-10, 1.0, 0L, false);

        assertThat(belowZero.normalizedScore()).isEqualTo(-1e-10);
        assertThat(aboveOne.normalizedScore()).isEqualTo(1.0 + 1e-10);
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteNormalizedScore(double nonFinite) {
        // Math.abs(NaN - x) > tolerance is false, so the range guard used to let a NaN
        // normalizedScore through silently instead of failing closed.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SimilarityResult(nonFinite, 0.0, 0L, false))
                .withMessageContaining("normalizedScore");
    }

    @Test
    void rejectsNegativeComputedNanos() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SimilarityResult(0.5, 0.5, -1L, false))
                .withMessageContaining("computedNanos");
    }
}
