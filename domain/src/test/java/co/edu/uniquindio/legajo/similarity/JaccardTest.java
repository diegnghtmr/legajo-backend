package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Token-level {@code jaccard} capability (TRD §6.3, §13; PRD HU-1.1/HU-1.6): {@code |S_A ∩
 * S_B| / |S_A ∪ S_B|} over the token SETS built from the preprocessed token streams (a
 * {@link SimilarityInput#tokens()} list is deduplicated into a set here — repeated tokens do
 * not inflate either side).
 *
 * <p>The coefficient is already in [0,1] (TRD §6.3, "Ya está en [0,1]"): unlike Levenshtein
 * (raw edit distance vs. {@code 1 - D/max}) or Needleman–Wunsch (raw alignment score vs. the
 * bounded transform), Jaccard has no separate raw-vs-normalized split, so {@code rawValue}
 * equals {@code normalizedScore} for every input, including the both-empty edge case, where
 * the {@code |A∩B|/|A∪B| = 0/0} fundamento is undefined and TRD §6.3 fixes the result to
 * {@code 1.0} by convention (both numbers, not just the normalized one). {@code degenerate}
 * stays {@code false} always — TRD §6.3 reserves that flag exclusively for the TF-IDF
 * null-vector case, not for Jaccard's own empty-input convention (same pattern already
 * established for Levenshtein/NW's both-empty results).
 *
 * <p>Goldens for the two-non-empty-sets case are hand-computed from the set definitions
 * (TRD §13 does not carry a Jaccard-specific numeric golden beyond "disjoint = 0.0" and
 * "both empty = 1.0", so this reuses the same token pairs Levenshtein's golden tests use,
 * recomputed here as sets rather than edit distance):
 *
 * <pre>
 * ["the","cat","sat"] vs ["the","cat","sat","down"]:
 *   S_A = {cat,sat,the} (3), S_B = {cat,down,sat,the} (4)
 *   A∩B = {cat,sat,the} (3), A∪B = {cat,down,sat,the} (4) -&gt; 3/4 = 0.75
 *
 * ["a","b","c"] vs ["a","x","c"]:
 *   S_A = {a,b,c} (3), S_B = {a,c,x} (3)
 *   A∩B = {a,c} (2), A∪B = {a,b,c,x} (4) -&gt; 2/4 = 0.5
 * </pre>
 */
class JaccardTest {

    private static final double TOLERANCE = 1e-9;
    private final Jaccard jaccard = new Jaccard();

    @Test
    void identicalSequencesScoreOne() {
        SimilarityInput a = input("the", "cat", "sat");
        SimilarityInput b = input("the", "cat", "sat");

        SimilarityResult result = jaccard.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(result.rawValue()).isEqualTo(1.0);
        assertThat(result.degenerate()).isFalse();
    }

    @Test
    void oneTrailingTokenGivesIntersectionThreeUnionFourAndCoefficientThreeQuarters() {
        SimilarityInput a = input("the", "cat", "sat");
        SimilarityInput b = input("the", "cat", "sat", "down");

        SimilarityResult result = jaccard.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(0.75, within(TOLERANCE));
        assertThat(result.rawValue()).isCloseTo(0.75, within(TOLERANCE));
    }

    @Test
    void oneSubstitutionGivesIntersectionTwoUnionFourAndCoefficientOneHalf() {
        SimilarityInput a = input("a", "b", "c");
        SimilarityInput b = input("a", "x", "c");

        SimilarityResult result = jaccard.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(0.5, within(TOLERANCE));
        assertThat(result.rawValue()).isCloseTo(0.5, within(TOLERANCE));
    }

    @Test
    void disjointNonEmptySetsScoreZero() {
        SimilarityInput a = input("a", "b");
        SimilarityInput b = input("x", "y");

        SimilarityResult result = jaccard.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(result.rawValue()).isEqualTo(0.0);
        assertThat(result.degenerate()).isFalse();
    }

    @Test
    void bothEmptyTokenStreamsScoreOneAndAreNotDegenerate() {
        SimilarityInput a = input();
        SimilarityInput b = input();

        SimilarityResult result = jaccard.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(result.rawValue()).isEqualTo(1.0);
        assertThat(result.degenerate()).isFalse();
    }

    @Test
    void duplicateTokensWithinAStreamDoNotInflateEitherSet() {
        // S_A dedupes to {a,b}; S_B dedupes to {a}. A∩B = {a} (1), A∪B = {a,b} (2) -> 0.5.
        SimilarityInput a = input("a", "a", "b", "b", "b");
        SimilarityInput b = input("a", "a", "a");

        SimilarityResult result = jaccard.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(0.5, within(TOLERANCE));
    }

    @Test
    void computedNanosIsMeasuredAndNonNegative() {
        SimilarityResult result = jaccard.compute(input("a"), input("b"), SimilarityContext.EMPTY);

        assertThat(result.computedNanos()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void traceExposesSortedSetsIntersectionUnionSizesAndCoefficient() {
        SimilarityInput a = input("a", "b", "c");
        SimilarityInput b = input("a", "x", "c");

        Optional<AlgorithmTrace> traceOptional = jaccard.trace(a, b, SimilarityContext.EMPTY);

        assertThat(traceOptional).isPresent();
        JaccardTrace trace = (JaccardTrace) traceOptional.get();
        assertThat(trace.algorithmId()).isEqualTo("jaccard");
        // Deterministic ordering (documented on JaccardTrace): natural String order
        // (lexicographic, String.compareTo), independent of each input's token order.
        assertThat(trace.setA()).containsExactly("a", "b", "c");
        assertThat(trace.setB()).containsExactly("a", "c", "x");
        assertThat(trace.intersection()).containsExactly("a", "c");
        assertThat(trace.union()).containsExactly("a", "b", "c", "x");
        assertThat(trace.intersectionSize()).isEqualTo(2);
        assertThat(trace.unionSize()).isEqualTo(4);
        assertThat(trace.coefficient()).isCloseTo(0.5, within(TOLERANCE));
    }

    @Test
    void traceOnBothEmptyInputsHasEmptySetsAndCoefficientOne() {
        SimilarityInput a = input();
        SimilarityInput b = input();

        JaccardTrace trace = (JaccardTrace) jaccard.trace(a, b, SimilarityContext.EMPTY).orElseThrow();

        assertThat(trace.setA()).isEmpty();
        assertThat(trace.setB()).isEmpty();
        assertThat(trace.intersection()).isEmpty();
        assertThat(trace.union()).isEmpty();
        assertThat(trace.intersectionSize()).isZero();
        assertThat(trace.unionSize()).isZero();
        assertThat(trace.coefficient()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void computeAndTraceAgreeOnTheCoefficientForTheSameInputs() {
        // compute() and trace() derive the coefficient independently; this pins both call
        // paths to the exact same number for a representative spread of inputs (a
        // characterization test ahead of removing that duplication).
        assertCoefficientsAgree(input("the", "cat", "sat"), input("the", "cat", "sat", "down"));
        assertCoefficientsAgree(input("a", "b", "c"), input("a", "x", "c"));
        assertCoefficientsAgree(input("a", "b"), input("x", "y"));
        assertCoefficientsAgree(input(), input());
        assertCoefficientsAgree(input("a", "a", "b"), input("a"));
    }

    private void assertCoefficientsAgree(SimilarityInput a, SimilarityInput b) {
        double computed = jaccard.compute(a, b, SimilarityContext.EMPTY).normalizedScore();
        double traced = ((JaccardTrace) jaccard.trace(a, b, SimilarityContext.EMPTY).orElseThrow()).coefficient();

        assertThat(computed).isEqualTo(traced);
    }

    @Test
    void traceOnDisjointSetsHasEmptyIntersectionAndFullUnion() {
        SimilarityInput a = input("a", "b");
        SimilarityInput b = input("x", "y");

        JaccardTrace trace = (JaccardTrace) jaccard.trace(a, b, SimilarityContext.EMPTY).orElseThrow();

        assertThat(trace.intersection()).isEmpty();
        assertThat(trace.union()).containsExactly("a", "b", "x", "y");
        assertThat(trace.coefficient()).isCloseTo(0.0, within(TOLERANCE));
    }

    private static SimilarityInput input(String... tokens) {
        return new SimilarityInput(String.join(" ", tokens), List.of(tokens));
    }
}
