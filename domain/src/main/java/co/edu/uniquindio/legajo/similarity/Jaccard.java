package co.edu.uniquindio.legajo.similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Jaccard coefficient over token sets (TRD §6.3, §13; PRD HU-1.1/HU-1.6), hand-written under
 * R-02: {@code |S_A ∩ S_B| / |S_A ∪ S_B|}, where {@code S_A}/{@code S_B} are the sets built
 * from each side's preprocessed token stream (TRD §6.2) — repeated tokens within one stream
 * collapse to a single set member and do not inflate either side.
 *
 * <p>The coefficient is already in [0,1] (TRD §6.3, "Ya está en [0,1]"), so unlike
 * Levenshtein and Needleman–Wunsch there is no separate raw-score-vs-normalized-score split:
 * {@code rawValue} equals {@code normalizedScore} for every input. When {@code |S_A ∪ S_B| =
 * 0} (both token streams empty), the fundamento itself is the undefined {@code 0/0}, and TRD
 * §6.3 fixes both numbers to {@code 1.0} by convention; a non-empty disjoint pair scores
 * {@code 0.0} from the ordinary formula, needing no special case. {@code degenerate} stays
 * {@code false} always — the TRD reserves that flag exclusively for the TF-IDF null-vector
 * case (§6.3, "Vector nulo de TF-IDF"), not for Jaccard's own empty-input convention (the
 * same pattern already established for Levenshtein/NW's both-empty results).
 *
 * <p>The trace exposes both sets, their intersection and union (sorted ascending by natural
 * {@link String} order — see {@link JaccardTrace} for the ordering rationale), their sizes,
 * and the resulting coefficient (PRD HU-1.6).
 */
public final class Jaccard implements SimilarityAlgorithm {

    @Override
    public String id() {
        return "jaccard";
    }

    @Override
    public String displayName() {
        return "Jaccard";
    }

    @Override
    public AlgorithmKind kind() {
        return AlgorithmKind.CLASSIC;
    }

    @Override
    public SimilarityResult compute(SimilarityInput a, SimilarityInput b, SimilarityContext context) {
        long start = System.nanoTime();

        TreeSet<String> setA = new TreeSet<>(a.tokens());
        TreeSet<String> setB = new TreeSet<>(b.tokens());
        TreeSet<String> union = new TreeSet<>(setA);
        union.addAll(setB);

        double coefficient;
        if (union.isEmpty()) {
            coefficient = 1.0;
        } else {
            TreeSet<String> intersection = new TreeSet<>(setA);
            intersection.retainAll(setB);
            coefficient = (double) intersection.size() / union.size();
        }

        long computedNanos = System.nanoTime() - start;
        return new SimilarityResult(coefficient, coefficient, computedNanos);
    }

    @Override
    public Optional<AlgorithmTrace> trace(SimilarityInput a, SimilarityInput b, SimilarityContext context) {
        TreeSet<String> setA = new TreeSet<>(a.tokens());
        TreeSet<String> setB = new TreeSet<>(b.tokens());
        TreeSet<String> intersection = new TreeSet<>(setA);
        intersection.retainAll(setB);
        TreeSet<String> union = new TreeSet<>(setA);
        union.addAll(setB);

        int unionSize = union.size();
        double coefficient = unionSize == 0 ? 1.0 : (double) intersection.size() / unionSize;

        List<String> setAList = new ArrayList<>(setA);
        List<String> setBList = new ArrayList<>(setB);
        List<String> intersectionList = new ArrayList<>(intersection);
        List<String> unionList = new ArrayList<>(union);

        AlgorithmTrace trace = new JaccardTrace(
                id(), setAList, setBList, intersectionList.size(), unionSize, intersectionList, unionList,
                coefficient);
        return Optional.of(trace);
    }
}
