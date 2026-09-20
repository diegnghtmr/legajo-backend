package co.edu.uniquindio.legajo.similarity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Corpus-wide document-frequency index for {@code tfidf-cosine} (TRD §6.3, "Fórmulas
 * TF-IDF (fijadas)"): {@code df(t)} and {@code N} are computed once over every document's
 * preprocessed token stream in the whole corpus (TRD §6.2) — never over the two documents
 * selected for a pairwise comparison. This is why {@code tfidf-cosine} needs corpus-wide
 * state carried through {@link SimilarityContext} instead of deriving df/N from its own two
 * {@link SimilarityInput} arguments.
 *
 * <p>Framework-free and decoupled from the {@code corpus} package: this type is built from
 * plain token-stream lists, not {@code CorpusDocument}, so the {@code similarity} package
 * gains no dependency on {@code corpus}. Whichever caller assembles the corpus's
 * preprocessed token streams (an application-layer concern) builds one instance via
 * {@link #from(List)} and reuses it for every pairwise comparison in that run.
 *
 * <p>{@code equals}/{@code hashCode} compare {@code documentFrequency} and {@code corpusSize}
 * by content: this is a value type derived entirely from the corpus content it was built
 * from, so two indexes built from equal token streams must compare equal, not just two
 * references to the same instance. This also makes {@link SimilarityContext}, whose default
 * (record-generated) equality delegates to this field, behave as the value type it documents
 * itself to be.
 */
public final class TfIdfCorpusIndex {

    private final Map<String, Integer> documentFrequency;
    private final int corpusSize;

    private TfIdfCorpusIndex(Map<String, Integer> documentFrequency, int corpusSize) {
        this.documentFrequency = documentFrequency;
        this.corpusSize = corpusSize;
    }

    /**
     * Builds the index from {@code corpusTokenStreams}, one entry per corpus document's
     * preprocessed token stream. {@code N = corpusTokenStreams.size()} (TRD §6.3, "N =
     * tamaño del corpus = n = |corpus|").
     */
    public static TfIdfCorpusIndex from(List<List<String>> corpusTokenStreams) {
        Objects.requireNonNull(corpusTokenStreams, "corpusTokenStreams");

        Map<String, Integer> documentFrequency = new HashMap<>();
        for (List<String> tokens : corpusTokenStreams) {
            Objects.requireNonNull(tokens, "corpusTokenStreams must not contain a null token stream");
            Set<String> distinctTerms = new HashSet<>(tokens);
            for (String term : distinctTerms) {
                Objects.requireNonNull(term, "corpusTokenStreams must not contain a null token");
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        return new TfIdfCorpusIndex(Map.copyOf(documentFrequency), corpusTokenStreams.size());
    }

    /** {@code N}: the corpus size this index was built from (TRD §6.3). */
    public int corpusSize() {
        return corpusSize;
    }

    /** {@code df(t)}: how many corpus documents contain {@code term} at least once; 0 if none. */
    public int documentFrequency(String term) {
        Objects.requireNonNull(term, "term");
        return documentFrequency.getOrDefault(term, 0);
    }

    /** {@code idf(t) = ln((1 + N) / (1 + df(t))) + 1} (TRD §6.3, smoothed idf). */
    public double idf(String term) {
        return Math.log((1.0 + corpusSize) / (1.0 + documentFrequency(term))) + 1.0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TfIdfCorpusIndex that)) {
            return false;
        }
        return corpusSize == that.corpusSize && documentFrequency.equals(that.documentFrequency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(corpusSize, documentFrequency);
    }
}
