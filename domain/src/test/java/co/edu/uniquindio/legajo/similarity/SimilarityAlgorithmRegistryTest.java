package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SimilarityAlgorithmRegistry} is the framework-free counterpart of the Spring
 * {@code @Component} list injection described in TRD §4.4: infrastructure collects every
 * permitted {@link SimilarityAlgorithm} bean into a {@code List} and hands it to this
 * registry's constructor, keeping the domain module itself Spring-free.
 */
class SimilarityAlgorithmRegistryTest {

    private static final SimilarityAlgorithm LEVENSHTEIN = new Levenshtein();

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
    void rejectsDuplicateIds() {
        SimilarityAlgorithm duplicate = new Levenshtein();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SimilarityAlgorithmRegistry(List.of(LEVENSHTEIN, duplicate)))
                .withMessageContaining("levenshtein");
    }
}
