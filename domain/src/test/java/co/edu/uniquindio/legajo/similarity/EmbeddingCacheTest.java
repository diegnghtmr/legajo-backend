package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link EmbeddingCache} is the domain shape of one {@code embeddings-*.json} cache file
 * (TRD §9): a {@code corpusVersion}/{@code corpusSha256} binding, {@code model}/{@code
 * dimension} metadata, and the per-document {@link EmbeddingVector} entries.
 */
class EmbeddingCacheTest {

    private static EmbeddingVector vector(String documentId) {
        return new EmbeddingVector(documentId, "local", "all-MiniLM-L6-v2", 5.0, List.of(0.6, 0.8));
    }

    @Test
    void findReturnsTheVectorForAKnownDocumentId() {
        EmbeddingCache cache = new EmbeddingCache("1.0", "1.0", "abc", "all-MiniLM-L6-v2", 2, List.of(vector("d01")));

        assertThat(cache.find("d01")).contains(vector("d01"));
    }

    @Test
    void findReturnsEmptyForAnUnknownDocumentId() {
        EmbeddingCache cache = new EmbeddingCache("1.0", "1.0", "abc", "all-MiniLM-L6-v2", 2, List.of(vector("d01")));

        assertThat(cache.find("d02")).isEqualTo(Optional.empty());
    }

    @Test
    void rejectsAVectorWhoseDimensionDoesNotMatchTheCacheDimension() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmbeddingCache("1.0", "1.0", "abc", "all-MiniLM-L6-v2", 3, List.of(vector("d01"))))
                .withMessageContaining("d01");
    }

    @Test
    void rejectsANonPositiveDimension() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmbeddingCache("1.0", "1.0", "abc", "all-MiniLM-L6-v2", 0, List.of()));
    }
}
