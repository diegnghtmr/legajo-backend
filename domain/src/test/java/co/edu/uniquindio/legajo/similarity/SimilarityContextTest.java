package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SimilarityContext} is the extension point for cross-cutting, corpus-wide state a
 * future capability may need (e.g. the df/N statistics {@code tfidf-cosine} requires over
 * the whole corpus, TRD §6.3). It carries nothing yet: Levenshtein (S2) needs no shared
 * state, so this record starts empty and grows fields as later capabilities need them,
 * without changing the {@code compute(a, b, context)} shape on the sealed contract.
 */
class SimilarityContextTest {

    @Test
    void emptyConstantIsReusable() {
        assertThat(SimilarityContext.EMPTY).isEqualTo(new SimilarityContext());
    }
}
