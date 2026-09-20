package co.edu.uniquindio.legajo.similarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Full-vocabulary, L2-normalized TF-IDF vectors for every document in the corpus (TRD
 * §6.3's TF-IDF formulas, materialized over the whole corpus vocabulary rather than a
 * single pair). {@link TfIdfCosine}'s own {@code computeVectors} rebuilds weights per pair
 * over only the union of the two compared documents' terms — sound for a pair's cosine,
 * because a term absent from both contributes 0 to the dot product and to both raw norms,
 * so the pairwise cosine is identical either way (proved by
 * {@code TfIdfCorpusVectorsTest#fullVocabularyPairwiseCosineMatchesTfIdfCosinesOwnPairwiseResultTo1e9}).
 * It is not sufficient for RF2's Davies–Bouldin metric (TRD §6.5), which needs every
 * document's vector in one shared space to take a centroid (a component-wise mean) and
 * Euclidean distances between centroids — operations that are only meaningful when every
 * vector has the same, corpus-wide set of components in the same order.
 *
 * <p>Placed next to {@link TfIdfCorpusIndex} in {@code similarity}, not in {@code
 * clustering}, so the clustering engine and its {@link
 * co.edu.uniquindio.legajo.clustering.DistanceMatrix} stay free of TF-IDF-specific
 * vocabulary/term-weighting code; {@code clustering} only ever consumes the resulting
 * {@code List<List<Double>>} of L2-normalized vectors, the same shape it would consume for
 * an embedding-based representation's {@link EmbeddingVector#values()}.
 *
 * <p>Uses the exact formulas already fixed for {@code tfidf-cosine} (TRD §6.3), not a
 * re-derivation: {@code tf(t,d) = 1 + ln f(t,d)} when {@code f(t,d) > 0} else {@code 0};
 * {@code idf(t) = ln((1 + N) / (1 + df(t))) + 1}; {@code w(t,d) = tf(t,d) · idf(t)}; then
 * each document's raw weight vector is L2-normalized. The vocabulary is the sorted
 * ({@link TreeSet}) union of every corpus document's distinct terms, so component order is
 * deterministic and identical for every document (NFR-QA-04).
 */
public final class TfIdfCorpusVectors {

    private TfIdfCorpusVectors() {
    }

    /**
     * Materializes one L2-normalized TF-IDF vector per document in {@code
     * corpusTokenStreams}, in the same order, over the full corpus vocabulary described by
     * {@code index} (which must have been built from the very same {@code
     * corpusTokenStreams} via {@link TfIdfCorpusIndex#from(List)} — this method trusts the
     * caller for that pairing, the same contract {@link TfIdfCosine} places on {@code
     * context.tfIdfIndex()}).
     *
     * <p>A document whose raw weight vector is entirely zero (an empty token stream) cannot
     * be L2-normalized and is rejected, mirroring the fail-closed convention {@link
     * EmbeddingVector#normalize} already uses for an all-zero raw vector. TRD §6.3 documents
     * a zero-vector convention only for {@code tfidf-cosine}'s own pairwise degenerate case
     * (both/one compared abstract empty), not for this whole-corpus materialization; in
     * practice a corpus abstract that preprocesses to an empty token stream is not expected
     * (the reference corpus's shortest abstracts are far from empty), so this path exists as
     * an explicit guard rather than a documented convention.
     */
    public static List<List<Double>> vectorsOf(List<List<String>> corpusTokenStreams, TfIdfCorpusIndex index) {
        Objects.requireNonNull(corpusTokenStreams, "corpusTokenStreams");
        Objects.requireNonNull(index, "index");

        TreeSet<String> vocabulary = new TreeSet<>();
        List<Map<String, Integer>> frequenciesPerDocument = new ArrayList<>(corpusTokenStreams.size());
        for (List<String> tokens : corpusTokenStreams) {
            Objects.requireNonNull(tokens, "corpusTokenStreams must not contain a null token stream");
            Map<String, Integer> frequencies = new HashMap<>();
            for (String token : tokens) {
                Objects.requireNonNull(token, "corpusTokenStreams must not contain a null token");
                frequencies.merge(token, 1, Integer::sum);
            }
            vocabulary.addAll(frequencies.keySet());
            frequenciesPerDocument.add(frequencies);
        }
        List<String> terms = List.copyOf(vocabulary);

        List<List<Double>> vectors = new ArrayList<>(corpusTokenStreams.size());
        for (int documentIndex = 0; documentIndex < frequenciesPerDocument.size(); documentIndex++) {
            Map<String, Integer> frequencies = frequenciesPerDocument.get(documentIndex);
            double[] rawWeights = new double[terms.size()];
            double sumOfSquares = 0.0;
            for (int t = 0; t < terms.size(); t++) {
                String term = terms.get(t);
                int frequency = frequencies.getOrDefault(term, 0);
                double tf = termFrequency(frequency);
                double weight = tf * index.idf(term);
                rawWeights[t] = weight;
                sumOfSquares += weight * weight;
            }
            double norm = Math.sqrt(sumOfSquares);
            if (norm == 0.0) {
                throw new IllegalArgumentException(
                        "cannot L2-normalize an all-zero TF-IDF vector for corpus document index "
                                + documentIndex);
            }
            List<Double> normalized = new ArrayList<>(terms.size());
            for (double weight : rawWeights) {
                normalized.add(weight / norm);
            }
            vectors.add(List.copyOf(normalized));
        }
        return List.copyOf(vectors);
    }

    /** {@code tf(t,d) = 1 + ln f(t,d)} when {@code f(t,d) > 0}, else {@code 0} (TRD §6.3). */
    private static double termFrequency(int frequency) {
        return frequency > 0 ? 1.0 + Math.log(frequency) : 0.0;
    }
}
