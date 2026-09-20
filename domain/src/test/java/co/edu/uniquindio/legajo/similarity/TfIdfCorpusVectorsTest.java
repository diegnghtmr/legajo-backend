package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

/**
 * Full-vocabulary, L2-normalized TF-IDF vectors for the whole corpus (TRD §6.3's formulas,
 * needed by RF2's Davies–Bouldin metric, TRD §6.5, which needs every document's vector in
 * one shared space to take centroids and Euclidean distances — unlike {@link TfIdfCosine},
 * which only ever needs a pair's cosine and can safely restrict itself to the union of the
 * pair's own terms).
 */
class TfIdfCorpusVectorsTest {

    private static final double TOLERANCE = 1e-9;

    private static final List<List<String>> FOUR_DOCUMENT_CORPUS = List.of(
            List.of("cat", "sat", "mat"),
            List.of("cat", "sat", "dog"),
            List.of("dog", "runs"),
            List.of("cat", "runs"));

    @Test
    void everyVectorIsL2Normalized() {
        TfIdfCorpusIndex index = TfIdfCorpusIndex.from(FOUR_DOCUMENT_CORPUS);

        List<List<Double>> vectors = TfIdfCorpusVectors.vectorsOf(FOUR_DOCUMENT_CORPUS, index);

        assertThat(vectors).hasSize(4);
        for (List<Double> vector : vectors) {
            double normSquared = vector.stream().mapToDouble(v -> v * v).sum();
            assertThat(Math.sqrt(normSquared)).isCloseTo(1.0, within(TOLERANCE));
        }
    }

    @Test
    void everyVectorSharesTheFullCorpusVocabularyDimension() {
        // Vocabulary: cat, dog, mat, runs, sat -> 5 distinct terms.
        TfIdfCorpusIndex index = TfIdfCorpusIndex.from(FOUR_DOCUMENT_CORPUS);

        List<List<Double>> vectors = TfIdfCorpusVectors.vectorsOf(FOUR_DOCUMENT_CORPUS, index);

        assertThat(vectors).allSatisfy(vector -> assertThat(vector).hasSize(5));
    }

    /**
     * The claim {@link TfIdfCorpusVectors} documents: a full-vocabulary pairwise cosine
     * equals {@link TfIdfCosine}'s own pairwise-scoped cosine, because a term absent from
     * both compared documents contributes 0 to the dot product and to both raw norms either
     * way. Reuses {@code TfIdfCosineTest}'s corpus-wide golden (D0 vs D1 in a 4-document
     * corpus, cosine = 0.5622829957377211) so both independent computations are checked
     * against the same non-trivial case.
     */
    @Test
    void fullVocabularyPairwiseCosineMatchesTfIdfCosinesOwnPairwiseResultTo1e9() {
        TfIdfCorpusIndex index = TfIdfCorpusIndex.from(FOUR_DOCUMENT_CORPUS);
        List<List<Double>> vectors = TfIdfCorpusVectors.vectorsOf(FOUR_DOCUMENT_CORPUS, index);

        List<Double> vectorA = vectors.get(0);
        List<Double> vectorB = vectors.get(1);
        double fullVocabularyCosine = 0.0;
        for (int t = 0; t < vectorA.size(); t++) {
            fullVocabularyCosine += vectorA.get(t) * vectorB.get(t);
        }

        SimilarityInput a = input("cat", "sat", "mat");
        SimilarityInput b = input("cat", "sat", "dog");
        SimilarityContext context = SimilarityContext.withTfIdfIndex(index);
        SimilarityResult pairwiseResult = new TfIdfCosine().compute(a, b, context);

        assertThat(fullVocabularyCosine).isCloseTo(0.5622829957377211, within(TOLERANCE));
        assertThat(fullVocabularyCosine).isCloseTo(pairwiseResult.normalizedScore(), within(TOLERANCE));
        assertThat(fullVocabularyCosine).isCloseTo(pairwiseResult.rawValue(), within(TOLERANCE));
    }

    @Test
    void vectorsOfRejectsANullCorpus() {
        TfIdfCorpusIndex index = TfIdfCorpusIndex.from(FOUR_DOCUMENT_CORPUS);

        assertThatNullPointerException().isThrownBy(() -> TfIdfCorpusVectors.vectorsOf(null, index));
    }

    @Test
    void vectorsOfRejectsANullIndex() {
        assertThatNullPointerException()
                .isThrownBy(() -> TfIdfCorpusVectors.vectorsOf(FOUR_DOCUMENT_CORPUS, null));
    }

    @Test
    void vectorsOfRejectsAnEmptyTokenStreamDocumentAsAnAllZeroVector() {
        // No TRD-fixed convention exists for a full-corpus all-zero TF-IDF vector (only for
        // tfidf-cosine's own pairwise degenerate case); this fails closed, matching
        // EmbeddingVector.normalize's convention for an all-zero raw vector.
        List<List<String>> corpusWithAnEmptyDocument = List.of(
                List.of("cat", "sat"),
                List.of());
        TfIdfCorpusIndex index = TfIdfCorpusIndex.from(corpusWithAnEmptyDocument);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> TfIdfCorpusVectors.vectorsOf(corpusWithAnEmptyDocument, index))
                .withMessageContaining("all-zero");
    }

    private static SimilarityInput input(String... tokens) {
        return new SimilarityInput(String.join(" ", tokens), List.of(tokens));
    }
}
