package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

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

    @Test
    void rejectsNegativeComputedNanos() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SimilarityResult(0.5, 0.5, -1L, false))
                .withMessageContaining("computedNanos");
    }
}
