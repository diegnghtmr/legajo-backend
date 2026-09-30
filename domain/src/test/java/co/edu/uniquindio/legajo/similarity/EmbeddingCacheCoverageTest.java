package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingCacheCoverageTest {

    private static EmbeddingVector vector(String id) {
        return new EmbeddingVector(id, "p", "m", 1.0, List.of(0.6, 0.8));
    }

    private static EmbeddingCache cache(String... ids) {
        return new EmbeddingCache("1.0", "1.0", "sha", "m", 2,
                java.util.Arrays.stream(ids).map(EmbeddingCacheCoverageTest::vector).toList());
    }

    @Test
    void aCacheHoldingExactlyOneVectorPerCorpusIdHasNoMismatch() {
        assertThat(EmbeddingCacheCoverage.mismatch(Set.of("d01", "d02"), cache("d02", "d01"))).isEmpty();
    }

    @Test
    void aMissingIdIsReported() {
        assertThat(EmbeddingCacheCoverage.mismatch(Set.of("d01", "d02"), cache("d01")))
                .hasValueSatisfying(message -> assertThat(message).contains("missing").contains("d02"));
    }

    @Test
    void anUnexpectedIdIsReported() {
        assertThat(EmbeddingCacheCoverage.mismatch(Set.of("d01"), cache("d01", "d99")))
                .hasValueSatisfying(message -> assertThat(message).contains("unexpected").contains("d99"));
    }

    @Test
    void aDuplicatedIdIsReported() {
        assertThat(EmbeddingCacheCoverage.mismatch(Set.of("d01", "d02"), cache("d01", "d01", "d02")))
                .hasValueSatisfying(message -> assertThat(message).contains("duplicate").contains("d01"));
    }
}
