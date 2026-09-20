package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Token-level {@code needleman-wunsch} capability (TRD §6.3, ADR-012, §13). Scoring
 * constants are fixed: match {@code +1}, mismatch {@code -1}, gap {@code -1}.
 *
 * <p>Golden matrices below are hand-computed from the recurrence
 * {@code dp[i][j] = max(dp[i-1][j-1] + score, dp[i-1][j] - 1, dp[i][j-1] - 1)} with
 * {@code dp[i][0] = -i}, {@code dp[0][j] = -j} — not read off a running implementation.
 */
class NeedlemanWunschTest {

    private static final double TOLERANCE = 1e-9;
    private final NeedlemanWunsch needlemanWunsch = new NeedlemanWunsch();

    @Test
    void identicalSequencesScoreOne() {
        // 3 matches: S = 3*1 = 3; m = M = 3; normalized = (S+M)/(2M) = 6/6 = 1.0.
        SimilarityInput a = input("a", "b", "c");
        SimilarityInput b = input("a", "b", "c");

        SimilarityResult result = needlemanWunsch.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.rawValue()).isEqualTo(3.0);
        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(result.degenerate()).isFalse();
    }

    @Test
    void equalLengthTotalMismatchScoresZero() {
        // 3 mismatches: S = 3*(-1) = -3; m = M = 3; normalized = (S+M)/(2M) = 0/6 = 0.0.
        SimilarityInput a = input("a", "b", "c");
        SimilarityInput b = input("x", "y", "z");

        SimilarityResult result = needlemanWunsch.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.rawValue()).isEqualTo(-3.0);
        assertThat(result.normalizedScore()).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void bothEmptyTokenStreamsScoreOneAndAreNotDegenerate() {
        SimilarityInput a = input();
        SimilarityInput b = input();

        SimilarityResult result = needlemanWunsch.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.rawValue()).isEqualTo(0.0);
        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(result.degenerate()).isFalse();
    }

    @Test
    void differingLengthsHandComputedAlignmentMatchesBothNormalizationForms() {
        // A = [a,b] (m=2), B = [a,b,c] (M=3). Hand-computed matrix (see class javadoc):
        //      ""   a   b   c
        //  ""    0  -1  -2  -3
        //  a    -1   1   0  -1
        //  b    -2   0   2   1
        // S = dp[2][3] = 1.
        SimilarityInput a = input("a", "b");
        SimilarityInput b = input("a", "b", "c");

        SimilarityResult result = needlemanWunsch.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.rawValue()).isEqualTo(1.0);

        // TRD §6.3 fixed form: (S - S_min) / (S_max - S_min), S_min = mismatch*m + gap*(M-m),
        // S_max = match*M.
        double sMin = -1.0 * 2 + -1.0 * (3 - 2);
        double sMax = 1.0 * 3;
        double fixedForm = (1.0 - sMin) / (sMax - sMin);
        // TRD §6.3 reduced form with the fixed constants substituted: (S + M) / (2*M).
        double reducedForm = (1.0 + 3.0) / (2.0 * 3.0);

        assertThat(fixedForm).isCloseTo(reducedForm, within(TOLERANCE));
        assertThat(result.normalizedScore()).isCloseTo(fixedForm, within(TOLERANCE));
        assertThat(result.normalizedScore()).isCloseTo(2.0 / 3.0, within(TOLERANCE));
    }

    @Test
    void computedNanosIsMeasuredAndNonNegative() {
        SimilarityResult result = needlemanWunsch.compute(input("a"), input("b"), SimilarityContext.EMPTY);

        assertThat(result.computedNanos()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void traceMatrixHasExpectedDimensionsAndValues() {
        SimilarityInput a = input("a", "b");
        SimilarityInput b = input("a", "b", "c");

        Optional<AlgorithmTrace> traceOptional = needlemanWunsch.trace(a, b, SimilarityContext.EMPTY);

        assertThat(traceOptional).isPresent();
        DpMatrixTrace trace = (DpMatrixTrace) traceOptional.get();
        assertThat(trace.algorithmId()).isEqualTo("needleman-wunsch");
        assertThat(trace.rowLabels()).containsExactly("a", "b");
        assertThat(trace.columnLabels()).containsExactly("a", "b", "c");
        double[][] expected = {
                {0, -1, -2, -3},
                {-1, 1, 0, -1},
                {-2, 0, 2, 1},
        };
        assertThat(trace.matrix()).isDeepEqualTo(expected);
    }

    @Test
    void traceOptimalPathIsContiguousAndEndsAtOrigin() {
        SimilarityInput a = input("a", "b");
        SimilarityInput b = input("a", "b", "c");

        DpMatrixTrace trace = (DpMatrixTrace) needlemanWunsch.trace(a, b, SimilarityContext.EMPTY).orElseThrow();

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
    void traceClassifiesGapAndMatchOperationsAlongTheHandComputedPath() {
        // From the hand-computed matrix: (2,3) -> (2,2) is the only maximal move (gap,
        // consuming B's "c" against a gap in A); (2,2) -> (1,1) is a tied diagonal
        // (b/b match, value 2) that the fixed order picks over the tied up/left moves at
        // this cell; (1,1) -> (0,0) is a tied diagonal (a/a match, value 1).
        SimilarityInput a = input("a", "b");
        SimilarityInput b = input("a", "b", "c");

        DpMatrixTrace trace = (DpMatrixTrace) needlemanWunsch.trace(a, b, SimilarityContext.EMPTY).orElseThrow();

        List<DpTraceStep> operations = trace.operations();
        assertThat(operations.get(0).to()).isEqualTo(new MatrixCell(2, 3));
        assertThat(operations.get(0).from()).isEqualTo(new MatrixCell(2, 2));
        assertThat(operations.get(0).operation()).isEqualTo(DpOperationKind.GAP);

        assertThat(operations.get(1).to()).isEqualTo(new MatrixCell(2, 2));
        assertThat(operations.get(1).from()).isEqualTo(new MatrixCell(1, 1));
        assertThat(operations.get(1).operation()).isEqualTo(DpOperationKind.MATCH);

        assertThat(operations.get(2).to()).isEqualTo(new MatrixCell(1, 1));
        assertThat(operations.get(2).from()).isEqualTo(new MatrixCell(0, 0));
        assertThat(operations.get(2).operation()).isEqualTo(DpOperationKind.MATCH);
    }

    @Test
    void traceBacktraceBreaksAThreeWayTieInFavorOfTheDiagonal() {
        // A = [a,b,b], B = [b,a,a]: hand-computed matrix (verified by exhaustive search
        // over the recurrence, see the S3 evidence) is
        //      ""   b   a   a
        //  ""    0  -1  -2  -3
        //  a    -1  -1   0  -1
        //  b    -2   0  -1  -1
        //  b    -3  -1  -1  -2
        // At (3,3): diagonal (b/a mismatch, -2), up (-2), and left (-2) all tie at the
        // matrix's value of -2 -> the fixed order picks the diagonal (MISMATCH) first.
        SimilarityInput a = input("a", "b", "b");
        SimilarityInput b = input("b", "a", "a");

        SimilarityResult result = needlemanWunsch.compute(a, b, SimilarityContext.EMPTY);
        assertThat(result.rawValue()).isEqualTo(-2.0);

        DpMatrixTrace trace = (DpMatrixTrace) needlemanWunsch.trace(a, b, SimilarityContext.EMPTY).orElseThrow();
        double[][] expected = {
                {0, -1, -2, -3},
                {-1, -1, 0, -1},
                {-2, 0, -1, -1},
                {-3, -1, -1, -2},
        };
        assertThat(trace.matrix()).isDeepEqualTo(expected);

        DpTraceStep firstStep = trace.operations().get(0);
        assertThat(firstStep.to()).isEqualTo(new MatrixCell(3, 3));
        assertThat(firstStep.from()).isEqualTo(new MatrixCell(2, 2));
        assertThat(firstStep.operation()).isEqualTo(DpOperationKind.MISMATCH);

        // At (2,2)=-1: diagonal (b[1]/a[1] mismatch) = -2, not tied; up and left both tie
        // at -1 -> the fixed order picks up (a GAP move, i decreases) over left.
        DpTraceStep secondStep = trace.operations().get(1);
        assertThat(secondStep.to()).isEqualTo(new MatrixCell(2, 2));
        assertThat(secondStep.from()).isEqualTo(new MatrixCell(1, 2));
        assertThat(secondStep.operation()).isEqualTo(DpOperationKind.GAP);
    }

    @Test
    void traceOnBothEmptyInputsIsASingleCellPathWithNoOperations() {
        SimilarityInput a = input();
        SimilarityInput b = input();

        DpMatrixTrace trace = (DpMatrixTrace) needlemanWunsch.trace(a, b, SimilarityContext.EMPTY).orElseThrow();

        assertThat(trace.rowLabels()).isEmpty();
        assertThat(trace.columnLabels()).isEmpty();
        assertThat(trace.matrix()).isDeepEqualTo(new double[][] {{0}});
        assertThat(trace.optimalPath()).containsExactly(new MatrixCell(0, 0));
        assertThat(trace.operations()).isEmpty();
    }

    private static SimilarityInput input(String... tokens) {
        return new SimilarityInput(String.join(" ", tokens), List.of(tokens));
    }
}
