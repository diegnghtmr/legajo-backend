package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SimilarityAlgorithmRegistry} is the framework-free collector that {@code
 * bootstrap}'s {@code DomainConfiguration} feeds: each of the six algorithms is declared
 * as its own {@code @Bean} factory method, in TRD §6.3's fixed order, and Spring's ordered
 * list injection collects the resulting {@code List<SimilarityAlgorithm>} into this
 * registry's constructor — the domain module itself never depends on Spring, and never
 * sees an annotation.
 */
class SimilarityAlgorithmRegistryTest {

    private static final SimilarityAlgorithm LEVENSHTEIN = new Levenshtein();
    private static final SimilarityAlgorithm EMBEDDING_LOCAL = new EmbeddingLocal();

    @Test
    void requireReturnsTheAlgorithmForItsId() {
        SimilarityAlgorithmRegistry registry = new SimilarityAlgorithmRegistry(List.of(LEVENSHTEIN));

        assertThat(registry.require("levenshtein")).isSameAs(LEVENSHTEIN);
    }

    @Test
    void requireThrowsForAnUnknownId() {
        SimilarityAlgorithmRegistry registry = new SimilarityAlgorithmRegistry(List.of(LEVENSHTEIN));

        assertThatThrownBy(() -> registry.require("jaccard"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("jaccard");
    }

    @Test
    void findReturnsEmptyForAnUnknownId() {
        SimilarityAlgorithmRegistry registry = new SimilarityAlgorithmRegistry(List.of(LEVENSHTEIN));

        assertThat(registry.find("jaccard")).isEqualTo(Optional.empty());
    }

    @Test
    void allReturnsEveryRegisteredAlgorithm() {
        SimilarityAlgorithmRegistry registry = new SimilarityAlgorithmRegistry(List.of(LEVENSHTEIN));

        assertThat(registry.all()).containsExactly(LEVENSHTEIN);
    }

    @Test
    void ofKindFiltersByAlgorithmKind() {
        SimilarityAlgorithmRegistry registry = new SimilarityAlgorithmRegistry(List.of(LEVENSHTEIN));

        assertThat(registry.ofKind(AlgorithmKind.CLASSIC)).containsExactly(LEVENSHTEIN);
        assertThat(registry.ofKind(AlgorithmKind.AI)).isEmpty();
    }

    @Test
    void ofKindReturnsAiCapabilitiesLikeEmbeddingLocal() {
        SimilarityAlgorithmRegistry registry = new SimilarityAlgorithmRegistry(List.of(LEVENSHTEIN, EMBEDDING_LOCAL));

        assertThat(registry.ofKind(AlgorithmKind.AI)).containsExactly(EMBEDDING_LOCAL);
        assertThat(registry.require("embedding-local")).isSameAs(EMBEDDING_LOCAL);
    }

    @Test
    void requireRejectsANullIdWithAClearMessage() {
        SimilarityAlgorithmRegistry registry = new SimilarityAlgorithmRegistry(List.of(LEVENSHTEIN));

        assertThatNullPointerException()
                .isThrownBy(() -> registry.require(null))
                .withMessageContaining("id");
    }

    @Test
    void findRejectsANullIdWithAClearMessage() {
        SimilarityAlgorithmRegistry registry = new SimilarityAlgorithmRegistry(List.of(LEVENSHTEIN));

        assertThatNullPointerException()
                .isThrownBy(() -> registry.find(null))
                .withMessageContaining("id");
    }

    @Test
    void rejectsDuplicateIds() {
        SimilarityAlgorithm duplicate = new Levenshtein();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SimilarityAlgorithmRegistry(List.of(LEVENSHTEIN, duplicate)))
                .withMessageContaining("levenshtein");
    }
}
