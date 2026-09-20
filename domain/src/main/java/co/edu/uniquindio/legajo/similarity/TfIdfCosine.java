package co.edu.uniquindio.legajo.similarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * TF-IDF cosine similarity over preprocessed token streams (TRD §6.3, "Fórmulas TF-IDF
 * (fijadas)"; PRD HU-1.3), hand-written under R-02: {@code tf(t,d) = 1 + ln f(t,d)} when
 * {@code f(t,d) > 0}, else {@code 0}; {@code idf(t) = ln((1 + N) / (1 + df(t))) + 1}
 * (smoothed); {@code w(t,d) = tf(t,d) · idf(t)}; both vectors L2-normalized; cosine = the
 * dot product of the normalized vectors; angle = {@code arccos(cosine)} in degrees.
 *
 * <p><b>Corpus-wide df/N (TRD §6.3, "nunca sobre el par seleccionado").</b> Unlike the other
 * three classical capabilities, this one needs state beyond its two {@link SimilarityInput}
 * arguments: {@code df(t)} and {@code N} are computed once over the whole corpus. That state
 * is a {@link TfIdfCorpusIndex}, carried through {@link SimilarityContext#tfIdfIndex()} —
 * required (non-null) for every non-degenerate computation.
 *
 * <p><b>Degenerate cases (TRD §6.3, "Vector nulo de TF-IDF (fijado)").</b> Both token
 * streams empty → {@code normalizedScore = 1.0}; exactly one empty → {@code 0.0}; in both
 * cases {@code rawValue = null} and {@code degenerate = true}. Both branches are
 * short-circuited before the corpus index is consulted at all: the "one empty" case's
 * mathematically genuine cosine is 0 regardless of any term's idf (one whole vector is the
 * zero vector, so every product in the dot sum is zero), and the "both empty" case has no
 * terms to weigh at all (the underlying {@code 0/0} is undefined and TRD fixes it to
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
            normalizedScore = computation.cosine();
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
            return Optional.of(new TfIdfCosineTrace(id(), 0, List.of(), 1.0, 0.0, 0.0, 1.0, 0.0));
        }
        if (emptyA || emptyB) {
            return Optional.of(new TfIdfCosineTrace(id(), 0, List.of(), 0.0, 0.0, 0.0, 0.0, 90.0));
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
                "context.tfIdfIndex() is required for tfidf-cosine (TRD §6.3: df/N over the whole corpus)");
    }

    /**
     * Runs the fixed formula over the union of terms present in {@code tokensA} or
     * {@code tokensB} (TRD §6.3; scope rationale on {@link TfIdfCosineTrace}), in two
     * passes: raw weights and both raw norms first, then each term's normalized weight
     * (TRD §6.3's explicit "segundo paso") and the running dot product, which — over
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

    /** {@code tf(t,d) = 1 + ln f(t,d)} when {@code f(t,d) > 0}, else {@code 0} (TRD §6.3). */
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
