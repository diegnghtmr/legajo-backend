package co.edu.uniquindio.legajo.similarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * TF-IDF cosine similarity over preprocessed token streams, hand-written; no library
 * implements it: {@code tf(t,d) = 1 + ln f(t,d)} when
 * {@code f(t,d) > 0}, else {@code 0}; {@code idf(t) = ln((1 + N) / (1 + df(t))) + 1}
 * (smoothed); {@code w(t,d) = tf(t,d) · idf(t)}; both vectors L2-normalized; cosine = the
 * dot product of the normalized vectors; angle = {@code arccos(cosine)} in degrees.
 *
 * <p><b>Corpus-wide df/N (never over the selected pair).</b> Unlike the other
 * three classical capabilities, this one needs state beyond its two {@link SimilarityInput}
 * arguments: {@code df(t)} and {@code N} are computed once over the whole corpus. That state
 * is a {@link TfIdfCorpusIndex}, carried through {@link SimilarityContext#tfIdfIndex()} —
 * required (non-null) for every non-degenerate computation.
 *
 * <p><b>Degenerate cases (the fixed TF-IDF null-vector convention).</b> Both token
 * streams empty → {@code normalizedScore = 1.0}; exactly one empty → {@code 0.0}; in both
 * cases {@code rawValue = null} and {@code degenerate = true}. Both branches are
 * short-circuited before the corpus index is consulted at all: the "one empty" case's
 * mathematically genuine cosine is 0 regardless of any term's idf (one whole vector is the
 * zero vector, so every product in the dot sum is zero), and the "both empty" case has no
 * terms to weigh at all (the underlying {@code 0/0} is undefined and this is fixed to
 * {@code 1.0} by convention, mirroring Jaccard's and Needleman-Wunsch's own empty-input
 * conventions) — so neither branch has genuine per-term evidence to show, and their traces
 * both report an empty term list with the same fixed convention values.
 *
 * <p><b>Term listing scope and trace shape</b> are documented on {@link TfIdfCosineTrace}.
 */
public final class TfIdfCosine implements SimilarityAlgorithm {

    @Override
    public String id() {
        return "tfidf-cosine";
    }

    @Override
    public String displayName() {
        return "TF-IDF Cosine";
    }

    @Override
    public AlgorithmKind kind() {
        return AlgorithmKind.CLASSIC;
    }

    @Override
    public SimilarityResult compute(SimilarityInput a, SimilarityInput b, SimilarityContext context) {
        long start = System.nanoTime();

        List<String> tokensA = a.tokens();
        List<String> tokensB = b.tokens();
        boolean emptyA = tokensA.isEmpty();
        boolean emptyB = tokensB.isEmpty();

        double normalizedScore;
        Double rawValue;
        boolean degenerate;
        if (emptyA && emptyB) {
            normalizedScore = 1.0;
            rawValue = null;
            degenerate = true;
        } else if (emptyA || emptyB) {
            normalizedScore = 0.0;
            rawValue = null;
            degenerate = true;
        } else {
            TfIdfCorpusIndex index = requireIndex(context);
            Computation computation = computeVectors(tokensA, tokensB, index);
            // TF-IDF weights are always non-negative (idf >= 1, tf >= 0), so the cosine of
            // two normalized vectors is mathematically bounded by [0,1]; clamp it explicitly
            // instead of relying solely on SimilarityResult's downstream tolerance-based
            // range guard to absorb any floating-point summation drift. rawValue keeps the
            // unclamped computed cosine as evidence.
            normalizedScore = clamp01(computation.cosine());
            rawValue = computation.cosine();
            degenerate = false;
        }

        long computedNanos = System.nanoTime() - start;
        return new SimilarityResult(normalizedScore, rawValue, computedNanos, degenerate);
    }

    @Override
    public Optional<AlgorithmTrace> trace(SimilarityInput a, SimilarityInput b, SimilarityContext context) {
        List<String> tokensA = a.tokens();
        List<String> tokensB = b.tokens();
        boolean emptyA = tokensA.isEmpty();
        boolean emptyB = tokensB.isEmpty();

        if (emptyA && emptyB) {
            return Optional.of(new TfIdfCosineTrace(id(), corpusSizeOf(context), List.of(), 1.0, 0.0, 0.0, 1.0, 0.0));
        }
        if (emptyA || emptyB) {
            return Optional.of(new TfIdfCosineTrace(id(), corpusSizeOf(context), List.of(), 0.0, 0.0, 0.0, 0.0, 90.0));
        }

        TfIdfCorpusIndex index = requireIndex(context);
        Computation computation = computeVectors(tokensA, tokensB, index);
        AlgorithmTrace trace = new TfIdfCosineTrace(
                id(), index.corpusSize(), computation.terms(), computation.dotProduct(),
                computation.rawNormA(), computation.rawNormB(), computation.cosine(), computation.angleDegrees());
        return Optional.of(trace);
    }

    private static TfIdfCorpusIndex requireIndex(SimilarityContext context) {
        return Objects.requireNonNull(context.tfIdfIndex(),
                "context.tfIdfIndex() is required for tfidf-cosine: df/N over the whole corpus");
    }

    /**
     * The corpus size to report on a degenerate trace (both-empty or exactly-one-empty
     * token streams), where no per-term evidence exists and the corpus index is never
     * consulted for df/N. {@code 0} only when the caller genuinely has no index to offer
     * (e.g. {@link SimilarityContext#EMPTY}); otherwise the index's real {@code N}, so this
     * field never misleadingly reads as "empty corpus" when the actual corpus is not.
     */
    private static int corpusSizeOf(SimilarityContext context) {
        TfIdfCorpusIndex index = context.tfIdfIndex();
        return index == null ? 0 : index.corpusSize();
    }

    /**
     * Runs the fixed formula over the union of terms present in {@code tokensA} or
     * {@code tokensB} (scope rationale on {@link TfIdfCosineTrace}), in two
     * passes: raw weights and both raw norms first, then each term's normalized weight
     * (the fixed, explicit second-pass step) and the running dot product, which — over
     * L2-normalized vectors — is the cosine itself.
     */
    private static Computation computeVectors(List<String> tokensA, List<String> tokensB, TfIdfCorpusIndex index) {
        Map<String, Integer> frequenciesA = frequenciesOf(tokensA);
        Map<String, Integer> frequenciesB = frequenciesOf(tokensB);

        TreeSet<String> union = new TreeSet<>(frequenciesA.keySet());
        union.addAll(frequenciesB.keySet());

        List<RawTerm> rawTerms = new ArrayList<>(union.size());
        double rawNormASquared = 0.0;
        double rawNormBSquared = 0.0;
        for (String term : union) {
            int frequencyA = frequenciesA.getOrDefault(term, 0);
            int frequencyB = frequenciesB.getOrDefault(term, 0);
            int documentFrequency = index.documentFrequency(term);
            double idf = index.idf(term);
            double tfA = termFrequency(frequencyA);
            double tfB = termFrequency(frequencyB);
            double rawWeightA = tfA * idf;
            double rawWeightB = tfB * idf;

            rawNormASquared += rawWeightA * rawWeightA;
            rawNormBSquared += rawWeightB * rawWeightB;
            rawTerms.add(new RawTerm(term, frequencyA, frequencyB, documentFrequency, tfA, tfB, idf,
                    rawWeightA, rawWeightB));
        }

        double rawNormA = Math.sqrt(rawNormASquared);
        double rawNormB = Math.sqrt(rawNormBSquared);

        List<TfIdfTermTrace> terms = new ArrayList<>(rawTerms.size());
        double dotProduct = 0.0;
        for (RawTerm rawTerm : rawTerms) {
            double normalizedWeightA = rawNormA == 0.0 ? 0.0 : rawTerm.rawWeightA() / rawNormA;
            double normalizedWeightB = rawNormB == 0.0 ? 0.0 : rawTerm.rawWeightB() / rawNormB;
            dotProduct += normalizedWeightA * normalizedWeightB;
            terms.add(new TfIdfTermTrace(rawTerm.term(), rawTerm.frequencyA(), rawTerm.frequencyB(),
                    rawTerm.documentFrequency(), rawTerm.tfA(), rawTerm.tfB(), rawTerm.idf(),
                    rawTerm.rawWeightA(), rawTerm.rawWeightB(), normalizedWeightA, normalizedWeightB));
        }

        double cosine = dotProduct;
        double clampedCosine = Math.max(-1.0, Math.min(1.0, cosine));
        double angleDegrees = Math.toDegrees(Math.acos(clampedCosine));

        return new Computation(terms, dotProduct, rawNormA, rawNormB, cosine, angleDegrees);
    }

    /**
     * {@code clamp(cosine, 0, 1)}. Package-private (not {@code private}) so
     * {@code TfIdfCosineTest} can exercise the boundary directly: the natural floating-point
     * drift for a corpus-sized TF-IDF cosine is far too small (empirically ~1e-15 even at a
     * million-term vocabulary) to trigger through the public API alone.
     */
    static double clamp01(double cosine) {
        NumericGuards.requireFinite(cosine, "cosine");
        return Math.max(0.0, Math.min(1.0, cosine));
    }

    /** {@code tf(t,d) = 1 + ln f(t,d)} when {@code f(t,d) > 0}, else {@code 0} (the fixed term-frequency formula). */
    private static double termFrequency(int frequency) {
        return frequency > 0 ? 1.0 + Math.log(frequency) : 0.0;
    }

    private static Map<String, Integer> frequenciesOf(List<String> tokens) {
        Map<String, Integer> frequencies = new HashMap<>();
        for (String token : tokens) {
            frequencies.merge(token, 1, Integer::sum);
        }
        return frequencies;
    }

    /** One term's intermediate values before either side's raw norm is known. */
    private record RawTerm(String term, int frequencyA, int frequencyB, int documentFrequency, double tfA,
            double tfB, double idf, double rawWeightA, double rawWeightB) {
    }

    /** The full result of one pairwise TF-IDF computation, shared by {@code compute} and {@code trace}. */
    private record Computation(List<TfIdfTermTrace> terms, double dotProduct, double rawNormA, double rawNormB,
            double cosine, double angleDegrees) {
    }
}
