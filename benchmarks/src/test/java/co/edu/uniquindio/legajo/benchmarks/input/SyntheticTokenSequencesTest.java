package co.edu.uniquindio.legajo.benchmarks.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the pairwise-curve token-sequence builder (the fixed performance-test
 * protocol's synthetic sequences of length L built by concatenating corpus tokens). Written
 * before {@link SyntheticTokenSequences} exists (strict TDD for the deterministic input
 * builders).
 */
class SyntheticTokenSequencesTest {

    private static final List<String> POOL = List.of(
            "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa");

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 50, 100, 200, 400, 800})
    void buildsBothSequencesAtTheRequestedLength(int length) {
        TokenSequencePair pair = SyntheticTokenSequences.build(POOL, length, 42L);

        assertThat(pair.sequenceA()).hasSize(length);
        assertThat(pair.sequenceB()).hasSize(length);
    }

    @Test
    void isDeterministicForAFixedSeed() {
        TokenSequencePair first = SyntheticTokenSequences.build(POOL, 800, 42L);
        TokenSequencePair second = SyntheticTokenSequences.build(POOL, 800, 42L);

        assertThat(first.sequenceA()).isEqualTo(second.sequenceA());
        assertThat(first.sequenceB()).isEqualTo(second.sequenceB());
    }

    @Test
    void differentSeedsCycleFromDifferentStartingOffsets() {
        TokenSequencePair seed42 = SyntheticTokenSequences.build(POOL, 50, 42L);
        TokenSequencePair seed7 = SyntheticTokenSequences.build(POOL, 50, 7L);

        assertThat(seed42.sequenceA()).isNotEqualTo(seed7.sequenceA());
    }

    @Test
    void theTwoSequencesOfOnePairAreNotTriviallyIdentical() {
        TokenSequencePair pair = SyntheticTokenSequences.build(POOL, 50, 42L);

        assertThat(pair.sequenceA()).isNotEqualTo(pair.sequenceB());
    }

    @Test
    void theTwoSequencesDifferEvenWhenThePoolHasOnlyTwoTokens() {
        List<String> twoTokenPool = List.of("alpha", "beta");

        TokenSequencePair pair = SyntheticTokenSequences.build(twoTokenPool, 4, 0L);

        assertThat(pair.sequenceA()).isNotEqualTo(pair.sequenceB());
    }

    @Test
    void everyTokenComesFromThePool() {
        TokenSequencePair pair = SyntheticTokenSequences.build(POOL, 800, 42L);

        assertThat(pair.sequenceA()).allMatch(POOL::contains);
        assertThat(pair.sequenceB()).allMatch(POOL::contains);
    }

    @Test
    void cyclesThePoolOnceLengthExceedsPoolSize() {
        TokenSequencePair pair = SyntheticTokenSequences.build(POOL, POOL.size() + 3, 0L);

        assertThat(pair.sequenceA().subList(POOL.size(), POOL.size() + 3))
                .isEqualTo(pair.sequenceA().subList(0, 3));
    }

    @Test
    void rejectsAnEmptyPool() {
        assertThatThrownBy(() -> SyntheticTokenSequences.build(List.of(), 10, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANegativeLength() {
        assertThatThrownBy(() -> SyntheticTokenSequences.build(POOL, -1, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
