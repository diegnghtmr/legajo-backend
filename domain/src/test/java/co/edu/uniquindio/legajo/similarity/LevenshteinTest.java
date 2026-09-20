package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Token-level {@code levenshtein} capability (TRD §6.3, §13). Golden values are quoted
 * from TRD §13 verbatim:
 *
 * <pre>
 * ["the","cat","sat"] vs ["the","cat","sat","down"] -&gt; D = 1, normalized 0.75
 * ["a","b","c"] vs ["a","x","c"] -&gt; D = 1, normalized ~ 0.6667
 * identical sequences -&gt; D = 0 -&gt; 1.0
 * empty vs empty -&gt; 1.0
 * </pre>
 */
class LevenshteinTest {

    private static final double TOLERANCE = 1e-9;
    private final Levenshtein levenshtein = new Levenshtein();

    @Test
    void identicalSequencesScoreOne() {
        SimilarityInput a = input("the", "cat", "sat");
        SimilarityInput b = input("the", "cat", "sat");

        SimilarityResult result = levenshtein.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(result.rawValue()).isEqualTo(0.0);
        assertThat(result.degenerate()).isFalse();
    }

    @Test
    void oneTrailingTokenGivesDistanceOneAndNormalizedZeroPointSevenFive() {
        SimilarityInput a = input("the", "cat", "sat");
        SimilarityInput b = input("the", "cat", "sat", "down");

        SimilarityResult result = levenshtein.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.rawValue()).isEqualTo(1.0);
        assertThat(result.normalizedScore()).isCloseTo(0.75, within(TOLERANCE));
    }

    @Test
    void oneSubstitutionGivesDistanceOneAndNormalizedTwoThirds() {
        SimilarityInput a = input("a", "b", "c");
        SimilarityInput b = input("a", "x", "c");

        SimilarityResult result = levenshtein.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.rawValue()).isEqualTo(1.0);
        assertThat(result.normalizedScore()).isCloseTo(2.0 / 3.0, within(TOLERANCE));
    }

    @Test
    void bothEmptyTokenStreamsScoreOneAndAreNotDegenerate() {
        SimilarityInput a = input();
        SimilarityInput b = input();

        SimilarityResult result = levenshtein.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(result.rawValue()).isEqualTo(0.0);
        assertThat(result.degenerate()).isFalse();
    }

    @Test
    void oneEmptyTokenStreamScoresZero() {
        SimilarityInput a = input();
        SimilarityInput b = input("the", "cat");

        SimilarityResult result = levenshtein.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.rawValue()).isEqualTo(2.0);
        assertThat(result.normalizedScore()).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void computedNanosIsMeasuredAndNonNegative() {
        SimilarityResult result = levenshtein.compute(input("a"), input("b"), SimilarityContext.EMPTY);

        assertThat(result.computedNanos()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void traceMatrixHasExpectedDimensionsAndBorderValues() {
        SimilarityInput a = input("a", "b");
        SimilarityInput b = input("a", "x", "b");

        Optional<AlgorithmTrace> traceOptional = levenshtein.trace(a, b, SimilarityContext.EMPTY);

        assertThat(traceOptional).isPresent();
        DpMatrixTrace trace = (DpMatrixTrace) traceOptional.get();
        assertThat(trace.algorithmId()).isEqualTo("levenshtein");
        assertThat(trace.rowLabels()).containsExactly("a", "b");
        assertThat(trace.columnLabels()).containsExactly("a", "x", "b");
        double[][] matrix = trace.matrix();
        assertThat(matrix).hasDimensions(3, 4);
        assertThat(matrix[0]).containsExactly(0.0, 1.0, 2.0, 3.0);
        for (int i = 0; i < matrix.length; i++) {
            assertThat(matrix[i][0]).isEqualTo((double) i);
        }
    }

    @Test
    void traceOptimalPathIsContiguousAndEndsAtOrigin() {
        SimilarityInput a = input("a", "b");
        SimilarityInput b = input("a", "x", "b");

        DpMatrixTrace trace = (DpMatrixTrace) levenshtein.trace(a, b, SimilarityContext.EMPTY).orElseThrow();

        List<MatrixCell> path = trace.optimalPath();
        assertThat(path.get(0)).isEqualTo(new MatrixCell(2, 3));
        assertThat(path.get(path.size() - 1)).isEqualTo(new MatrixCell(0, 0));
        for (int i = 1; i < path.size(); i++) {
            MatrixCell previous = path.get(i - 1);
            MatrixCell current = path.get(i);
            int rowDelta = previous.row() - current.row();
            int colDelta = previous.col() - current.col();
            assertThat(rowDelta).isBetween(0, 1);
            assertThat(colDelta).isBetween(0, 1);
            assertThat(rowDelta + colDelta).isGreaterThan(0);
        }
        assertThat(trace.operations()).hasSize(path.size() - 1);
    }

    @Test
    void traceClassifiesADeletionWhenASideHasAnExtraToken() {
        // A = [the, cat, sat, down] (extra "down"), B = [the, cat, sat]: the optimal
        // backtrace consumes A's trailing "down" via an "up" move, classified DELETION
        // (Levenshtein distinguishes "extra token in A" from "extra token in B", unlike
        // Needleman-Wunsch's direction-agnostic GAP) — this operation kind was otherwise
        // never asserted by any existing golden test.
        SimilarityInput a = input("the", "cat", "sat", "down");
        SimilarityInput b = input("the", "cat", "sat");

        DpMatrixTrace trace = (DpMatrixTrace) levenshtein.trace(a, b, SimilarityContext.EMPTY).orElseThrow();

        assertThat(trace.operations()).extracting(DpTraceStep::operation).contains(DpOperationKind.DELETION);
        DpTraceStep firstStep = trace.operations().get(0);
        assertThat(firstStep.operation()).isEqualTo(DpOperationKind.DELETION);
        assertThat(firstStep.to()).isEqualTo(new MatrixCell(4, 3));
        assertThat(firstStep.from()).isEqualTo(new MatrixCell(3, 3));
    }

    @Test
    void traceBacktraceBreaksATieInFavorOfTheDiagonal() {
        // "ab" vs "ba": at cell (2,2) diagonal (substitution, cost 2), up (deletion, cost 2)
        // and left (insertion, cost 2) all reach the same minimal value; the fixed order
        // must pick the diagonal first.
        SimilarityInput a = input("a", "b");
        SimilarityInput b = input("b", "a");

        DpMatrixTrace trace = (DpMatrixTrace) levenshtein.trace(a, b, SimilarityContext.EMPTY).orElseThrow();

        DpTraceStep firstStepFromTheEnd = trace.operations().get(0);
        assertThat(firstStepFromTheEnd.to()).isEqualTo(new MatrixCell(2, 2));
        assertThat(firstStepFromTheEnd.from()).isEqualTo(new MatrixCell(1, 1));
        assertThat(firstStepFromTheEnd.operation()).isEqualTo(DpOperationKind.SUBSTITUTION);
    }

    private static SimilarityInput input(String... tokens) {
        return new SimilarityInput(String.join(" ", tokens), List.of(tokens));
    }
}
