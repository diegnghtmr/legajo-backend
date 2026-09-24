package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SimilarityAlgorithm} identity contract for {@link EmbeddingApi}:
 * {@code id}/{@code displayName} for the registry and UI, {@code kind} groups it with
 * {@code embedding-local} as an AI capability, not a classical one.
 */
class EmbeddingApiIdentityTest {

    private final EmbeddingApi embeddingApi = new EmbeddingApi();

    @Test
    void hasTheAssignedIdAndDisplayName() {
        assertThat(embeddingApi.id()).isEqualTo("embedding-api");
        assertThat(embeddingApi.displayName()).isNotBlank();
    }

    @Test
    void isAnAiCapability() {
        assertThat(embeddingApi.kind()).isEqualTo(AlgorithmKind.AI);
    }
}
