package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * {@link SimilarityInput} carries both text representations the six
 * capabilities need: the classical capabilities and {@code tfidf-cosine} read
 * {@code tokens()} (the preprocessed stream); {@code embedding-local} and
 * {@code embedding-api} read {@code rawAbstract()} because each pretrained model owns
 * its own tokenizer (delegable). Bundling both in one record lets
 * {@code SimilarityAlgorithm} expose a single {@code compute(a, b, context)} shape
 * without branching on {@code kind()} before the call, and without a second,
 * kind-specific method pair on the sealed contract.
 */
class SimilarityInputTest {

    @Test
    void exposesBothRepresentations() {
        SimilarityInput input = new SimilarityInput("Raw abstract.", List.of("raw", "abstract"));

        assertThat(input.rawAbstract()).isEqualTo("Raw abstract.");
        assertThat(input.tokens()).containsExactly("raw", "abstract");
    }

    @Test
    void defensivelyCopiesTokens() {
        List<String> mutable = new ArrayList<>(List.of("a", "b"));
        SimilarityInput input = new SimilarityInput("a b", mutable);

        mutable.add("c");

        assertThat(input.tokens()).containsExactly("a", "b");
    }

    @Test
    void rejectsNullRawAbstract() {
        assertThatNullPointerException().isThrownBy(() -> new SimilarityInput(null, List.of()));
    }

    @Test
    void rejectsNullTokens() {
        assertThatNullPointerException().isThrownBy(() -> new SimilarityInput("text", null));
    }

    @Test
    void theTwoArgConstructorLeavesEmbeddingVectorNull() {
        SimilarityInput input = new SimilarityInput("Raw abstract.", List.of("raw", "abstract"));

        assertThat(input.embeddingVector()).isNull();
    }

    @Test
    void exposesAnExplicitlyProvidedEmbeddingVector() {
        EmbeddingVector embeddingVector =
                new EmbeddingVector("d01", "local", "all-MiniLM-L6-v2", 5.0, List.of(0.6, 0.8));

        SimilarityInput input = new SimilarityInput("Raw abstract.", List.of(), embeddingVector);

        assertThat(input.embeddingVector()).isEqualTo(embeddingVector);
    }
}
