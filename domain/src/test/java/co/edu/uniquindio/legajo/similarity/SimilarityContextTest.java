package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SimilarityContext} is the extension point for cross-cutting, corpus-wide state a
 * capability may need. Levenshtein, Needleman-Wunsch, and Jaccard need none,
 * so the no-arg constructor and {@link SimilarityContext#EMPTY} still carry a {@code null}
 * {@code tfIdfIndex}. {@code tfidf-cosine} is the first capability that needs the
 * corpus-wide df/N statistics, carried by {@link TfIdfCorpusIndex} — added here
 * without changing the {@code compute(a, b, context)} shape on the sealed contract.
 */
class SimilarityContextTest {

    @Test
    void emptyConstantIsReusable() {
        assertThat(SimilarityContext.EMPTY).isEqualTo(new SimilarityContext());
    }

    @Test
    void noArgConstructorHasNoTfIdfIndex() {
        assertThat(new SimilarityContext().tfIdfIndex()).isNull();
        assertThat(SimilarityContext.EMPTY.tfIdfIndex()).isNull();
    }

    @Test
    void withTfIdfIndexCarriesTheGivenIndex() {
        TfIdfCorpusIndex index = TfIdfCorpusIndex.from(List.of(List.of("a")));

        SimilarityContext context = SimilarityContext.withTfIdfIndex(index);

        assertThat(context.tfIdfIndex()).isSameAs(index);
    }

    @Test
    void contextsWrappingEqualButDistinctIndexesAreEqual() {
        // SimilarityContext is a value type (a record); its default equals delegates to
        // TfIdfCorpusIndex.equals(), so two contexts built from independently constructed but
        // content-equal indexes must compare equal, not just two contexts sharing one instance.
        TfIdfCorpusIndex first = TfIdfCorpusIndex.from(List.of(List.of("a")));
        TfIdfCorpusIndex second = TfIdfCorpusIndex.from(List.of(List.of("a")));

        assertThat(SimilarityContext.withTfIdfIndex(first)).isEqualTo(SimilarityContext.withTfIdfIndex(second));
    }
}
