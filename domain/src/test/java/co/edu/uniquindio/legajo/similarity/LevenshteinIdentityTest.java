package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S1 walking skeleton for {@link Levenshtein}: only the {@link SimilarityAlgorithm}
 * identity contract (id, displayName, kind) is exercised here, which is what the sealed
 * permits list and {@link SimilarityAlgorithmRegistry} need to compile and be tested
 * against a real permitted type. {@code compute}/{@code trace} behavior is test-driven in
 * S2 (see {@code LevenshteinTest}), which replaces this class's placeholder bodies.
 */
class LevenshteinIdentityTest {

    private final Levenshtein levenshtein = new Levenshtein();

    @Test
    void hasTheTrdAssignedIdAndDisplayName() {
        assertThat(levenshtein.id()).isEqualTo("levenshtein");
        assertThat(levenshtein.displayName()).isEqualTo("Levenshtein");
    }

    @Test
    void isAClassicCapability() {
        assertThat(levenshtein.kind()).isEqualTo(AlgorithmKind.CLASSIC);
    }
}
