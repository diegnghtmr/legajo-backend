package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link TfIdfCorpusIndex} computes {@code df(t)} and {@code N} over the whole corpus of
 * preprocessed token streams it is built from (TRD §6.3, "Fórmulas TF-IDF (fijadas)") — never
 * over any two documents selected for a pairwise comparison. This is exactly why
 * {@code TfIdfCosine} needs corpus-wide state through {@link SimilarityContext} instead of
 * deriving df/N from its own two {@link SimilarityInput} arguments.
 *
 * <p>{@code idf(t) = ln((1 + N) / (1 + df(t))) + 1}, hand-computed below for a 4-document
 * corpus: {@code cat} appears in 3 of 4 documents, {@code mat} in 1 of 4.
 */
class TfIdfCorpusIndexTest {

    private static final double TOLERANCE = 1e-9;

    // D0=[cat,sat,mat], D1=[cat,sat,dog], D2=[dog,runs], D3=[cat,runs]. N=4.
    private final TfIdfCorpusIndex index = TfIdfCorpusIndex.from(List.of(
            List.of("cat", "sat", "mat"),
            List.of("cat", "sat", "dog"),
            List.of("dog", "runs"),
            List.of("cat", "runs")));

    @Test
    void corpusSizeIsTheNumberOfTokenStreamsIndexed() {
        assertThat(index.corpusSize()).isEqualTo(4);
    }

    @Test
    void documentFrequencyCountsDistinctDocumentsContainingTheTerm() {
        assertThat(index.documentFrequency("cat")).isEqualTo(3);
        assertThat(index.documentFrequency("sat")).isEqualTo(2);
        assertThat(index.documentFrequency("mat")).isEqualTo(1);
        assertThat(index.documentFrequency("dog")).isEqualTo(2);
        assertThat(index.documentFrequency("runs")).isEqualTo(2);
    }

    @Test
    void documentFrequencyOfARepeatedTermWithinOneDocumentCountsThatDocumentOnce() {
        TfIdfCorpusIndex repeated = TfIdfCorpusIndex.from(List.of(List.of("cat", "cat", "cat")));

        assertThat(repeated.documentFrequency("cat")).isEqualTo(1);
        assertThat(repeated.corpusSize()).isEqualTo(1);
    }

    @Test
    void documentFrequencyOfAnUnseenTermIsZero() {
        assertThat(index.documentFrequency("unseen")).isZero();
    }

    @Test
    void idfUsesTheSmoothedFormulaOverCorpusWideDfAndN() {
        // idf(cat) = ln((1+4)/(1+3)) + 1 = ln(5/4) + 1
        assertThat(index.idf("cat")).isCloseTo(Math.log(5.0 / 4.0) + 1.0, within(TOLERANCE));
        // idf(mat) = ln((1+4)/(1+1)) + 1 = ln(5/2) + 1
        assertThat(index.idf("mat")).isCloseTo(Math.log(5.0 / 2.0) + 1.0, within(TOLERANCE));
        // idf(unseen) = ln((1+4)/(1+0)) + 1 = ln(5) + 1
        assertThat(index.idf("unseen")).isCloseTo(Math.log(5.0) + 1.0, within(TOLERANCE));
    }

    @Test
    void emptyCorpusHasZeroSizeAndZeroDocumentFrequencyEverywhere() {
        TfIdfCorpusIndex empty = TfIdfCorpusIndex.from(List.of());

        assertThat(empty.corpusSize()).isZero();
        assertThat(empty.documentFrequency("anything")).isZero();
        // idf(t) = ln((1+0)/(1+0)) + 1 = ln(1) + 1 = 1.0
        assertThat(empty.idf("anything")).isCloseTo(1.0, within(TOLERANCE));
    }
}
