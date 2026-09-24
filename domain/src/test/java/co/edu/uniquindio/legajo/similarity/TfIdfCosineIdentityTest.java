package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SimilarityAlgorithm} identity contract for {@link TfIdfCosine}:
 * {@code id}/{@code displayName} for the registry and UI, {@code kind} to group it with the
 * other classical capabilities.
 */
class TfIdfCosineIdentityTest {

    private final TfIdfCosine tfIdfCosine = new TfIdfCosine();

    @Test
    void hasTheAssignedIdAndDisplayName() {
        assertThat(tfIdfCosine.id()).isEqualTo("tfidf-cosine");
        assertThat(tfIdfCosine.displayName()).isEqualTo("TF-IDF Cosine");
    }

    @Test
    void isAClassicCapability() {
        assertThat(tfIdfCosine.kind()).isEqualTo(AlgorithmKind.CLASSIC);
    }
}
