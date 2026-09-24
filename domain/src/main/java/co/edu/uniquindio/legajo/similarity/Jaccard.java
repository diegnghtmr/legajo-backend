package co.edu.uniquindio.legajo.similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Jaccard coefficient over token sets, hand-written; no library implements it:
 * {@code |S_A ∩ S_B| / |S_A ∪ S_B|}, where {@code S_A}/{@code S_B} are the sets built
 * from each side's preprocessed token stream — repeated tokens within one stream
 * collapse to a single set member and do not inflate either side.
 *
 * <p>The coefficient is already in [0,1], so unlike
 * Levenshtein and Needleman–Wunsch there is no separate raw-score-vs-normalized-score split:
 * {@code rawValue} equals {@code normalizedScore} for every input. When {@code |S_A ∪ S_B| =
 * 0} (both token streams empty), the underlying formula itself is the undefined {@code 0/0},
 * and both numbers are fixed to {@code 1.0} by convention; a non-empty disjoint pair scores
 * {@code 0.0} from the ordinary formula, needing no special case. {@code degenerate} stays
 * {@code false} always — that flag is reserved exclusively for the TF-IDF null-vector
 * case, not for Jaccard's own empty-input convention (the
 * same pattern already established for Levenshtein/NW's both-empty results).
 *
 * <p>The trace exposes both sets, their intersection and union (sorted ascending by natural
 * {@link String} order — see {@link JaccardTrace} for the ordering rationale), their sizes,
 * and the resulting coefficient.
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

        double coefficient = setsOf(a, b).coefficient();

        long computedNanos = System.nanoTime() - start;
        return new SimilarityResult(coefficient, coefficient, computedNanos);
    }

    @Override
    public Optional<AlgorithmTrace> trace(SimilarityInput a, SimilarityInput b, SimilarityContext context) {
        Sets sets = setsOf(a, b);

        AlgorithmTrace trace = new JaccardTrace(
                id(), new ArrayList<>(sets.setA()), new ArrayList<>(sets.setB()), sets.intersection().size(),
                sets.union().size(), new ArrayList<>(sets.intersection()), new ArrayList<>(sets.union()),
                sets.coefficient());
        return Optional.of(trace);
    }

    /**
     * Builds both token sets, their intersection and union, and the resulting coefficient in
     * one place, so {@code compute()} and {@code trace()} never derive the coefficient
     * independently (they used to, duplicating the {@code |S_A ∩ S_B| / |S_A ∪ S_B|} formula
     * and its both-empty convention).
     */
    private static Sets setsOf(SimilarityInput a, SimilarityInput b) {
        TreeSet<String> setA = new TreeSet<>(a.tokens());
        TreeSet<String> setB = new TreeSet<>(b.tokens());
        TreeSet<String> union = new TreeSet<>(setA);
        union.addAll(setB);
        TreeSet<String> intersection = new TreeSet<>(setA);
        intersection.retainAll(setB);

        double coefficient = union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
        return new Sets(setA, setB, intersection, union, coefficient);
    }

    private record Sets(
            TreeSet<String> setA, TreeSet<String> setB, TreeSet<String> intersection, TreeSet<String> union,
            double coefficient) {
    }
}
