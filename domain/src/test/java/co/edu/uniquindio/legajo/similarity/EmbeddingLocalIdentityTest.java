package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SimilarityAlgorithm} identity contract for {@link EmbeddingLocal} (TRD §6.3):
 * {@code id}/{@code displayName} for the registry and UI, {@code kind} groups it with
 * {@code embedding-api} as an AI capability, not a classical one.
 */
class EmbeddingLocalIdentityTest {

    private final EmbeddingLocal embeddingLocal = new EmbeddingLocal();

    @Test
    void hasTheTrdAssignedIdAndDisplayName() {
        assertThat(embeddingLocal.id()).isEqualTo("embedding-local");
        assertThat(embeddingLocal.displayName()).isNotBlank();
    }

    @Test
    void isAnAiCapability() {
        assertThat(embeddingLocal.kind()).isEqualTo(AlgorithmKind.AI);
    }
}
