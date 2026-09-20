package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SimilarityAlgorithm} identity contract for {@link Jaccard} (TRD §6.3): {@code
 * id}/{@code displayName} for the registry and UI, {@code kind} to group it with the other
 * classical capabilities.
 */
class JaccardIdentityTest {

    private final Jaccard jaccard = new Jaccard();

    @Test
    void hasTheTrdAssignedIdAndDisplayName() {
        assertThat(jaccard.id()).isEqualTo("jaccard");
        assertThat(jaccard.displayName()).isEqualTo("Jaccard");
    }

    @Test
    void isAClassicCapability() {
        assertThat(jaccard.kind()).isEqualTo(AlgorithmKind.CLASSIC);
    }
}
