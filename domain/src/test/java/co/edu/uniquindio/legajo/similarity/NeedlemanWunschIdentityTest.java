package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SimilarityAlgorithm} identity contract for {@link NeedlemanWunsch}:
 * {@code id}/{@code displayName} for the registry and UI, {@code kind} to group
 * it with the other classical capabilities.
 */
class NeedlemanWunschIdentityTest {

    private final NeedlemanWunsch needlemanWunsch = new NeedlemanWunsch();

    @Test
    void hasTheAssignedIdAndDisplayName() {
        assertThat(needlemanWunsch.id()).isEqualTo("needleman-wunsch");
        assertThat(needlemanWunsch.displayName()).isEqualTo("Needleman-Wunsch");
    }

    @Test
    void isAClassicCapability() {
        assertThat(needlemanWunsch.kind()).isEqualTo(AlgorithmKind.CLASSIC);
    }
}
