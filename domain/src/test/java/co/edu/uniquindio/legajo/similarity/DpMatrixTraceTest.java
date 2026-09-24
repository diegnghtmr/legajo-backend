package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link DpMatrixTrace} is the shared trace model for the two DP algorithms
 * (Levenshtein and Needleman–Wunsch): the full matrix, row/column token labels, the
 * deterministic optimal path (fixed backtrace tie order: diagonal, up, left), and the
 * per-step operation classification. It is modeled as plain read-only data so a future
 * REST/CSV export (out of scope here) is a pure read of this record.
 */
class DpMatrixTraceTest {

    private static final List<String> ROW_LABELS = List.of("a", "b");
    private static final List<String> COLUMN_LABELS = List.of("a", "x", "b");

    @Test
    void exposesMatrixLabelsPathAndOperations() {
        double[][] matrix = {
                {0, 1, 2, 3},
                {1, 0, 1, 2},
                {2, 1, 1, 1},
        };
        List<MatrixCell> path = List.of(
                new MatrixCell(2, 3), new MatrixCell(1, 2), new MatrixCell(0, 1), new MatrixCell(0, 0));
        List<DpTraceStep> operations = List.of(
                new DpTraceStep(new MatrixCell(1, 2), new MatrixCell(2, 3), DpOperationKind.MATCH),
                new DpTraceStep(new MatrixCell(0, 1), new MatrixCell(1, 2), DpOperationKind.SUBSTITUTION),
                new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(0, 1), DpOperationKind.INSERTION));

        DpMatrixTrace trace = new DpMatrixTrace("levenshtein", ROW_LABELS, COLUMN_LABELS, matrix, path, operations);

        assertThat(trace.algorithmId()).isEqualTo("levenshtein");
        assertThat(trace.rowLabels()).containsExactly("a", "b");
        assertThat(trace.columnLabels()).containsExactly("a", "x", "b");
        assertThat(trace.matrix()).isDeepEqualTo(matrix);
        assertThat(trace.optimalPath()).containsExactlyElementsOf(path);
        assertThat(trace.operations()).containsExactlyElementsOf(operations);
    }

    @Test
    void isAnAlgorithmTrace() {
        DpMatrixTrace trace = minimalTrace();

        assertThat(trace).isInstanceOf(AlgorithmTrace.class);
    }

    @Test
    void defensivelyCopiesTheMatrixOnConstruction() {
        double[][] matrix = {{0, 1}, {1, 0}};
        DpMatrixTrace trace = new DpMatrixTrace("levenshtein", List.of("a"), List.of("a"), matrix,
                List.of(new MatrixCell(1, 1), new MatrixCell(0, 0)),
                List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 1), DpOperationKind.MATCH)));

        matrix[0][0] = 99;

        assertThat(trace.matrix()[0][0]).isEqualTo(0);
    }

    @Test
    void defensivelyCopiesTheReturnedMatrix() {
        DpMatrixTrace trace = minimalTrace();

        trace.matrix()[0][0] = 99;

        assertThat(trace.matrix()[0][0]).isEqualTo(0);
    }

    @Test
    void rejectsAMatrixWithTheWrongRowCount() {
        double[][] wrongRows = {{0, 1}}; // rowLabels has 1 token -> expects 2 rows

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DpMatrixTrace("levenshtein", List.of("a"), List.of("a"), wrongRows,
                        List.of(new MatrixCell(0, 0)), List.of()))
                .withMessageContaining("rows");
    }

    @Test
    void rejectsARaggedMatrix() {
        double[][] ragged = {{0, 1}, {1}};

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DpMatrixTrace("levenshtein", List.of("a"), List.of("a"), ragged,
                        List.of(new MatrixCell(1, 1), new MatrixCell(0, 0)), List.of()))
                .withMessageContaining("columns");
    }

    @Test
    void rejectsAPathThatDoesNotStartAtTheBottomRightCorner() {
        double[][] matrix = {{0, 1}, {1, 0}};

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DpMatrixTrace("levenshtein", List.of("a"), List.of("a"), matrix,
                        List.of(new MatrixCell(0, 0)), List.of()))
                .withMessageContaining("bottom-right corner");
    }

    @Test
    void rejectsAPathThatDoesNotEndAtTheOrigin() {
        double[][] matrix = {{0, 1}, {1, 0}};

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DpMatrixTrace("levenshtein", List.of("a"), List.of("a"), matrix,
                        List.of(new MatrixCell(1, 1)), List.of()))
                .withMessageContaining("(0,0)");
    }

    @Test
    void twoTracesWithEqualFieldsButDifferentMatrixInstancesAreEqual() {
        DpMatrixTrace first = minimalTrace();
        DpMatrixTrace second = minimalTrace();

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void tracesWithDifferentMatrixContentAreNotEqual() {
        DpMatrixTrace first = minimalTrace();
        double[][] differentMatrix = {{0, 1}, {1, 99}};
        DpMatrixTrace second = new DpMatrixTrace("levenshtein", List.of("a"), List.of("a"), differentMatrix,
                List.of(new MatrixCell(1, 1), new MatrixCell(0, 0)),
                List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 1), DpOperationKind.MATCH)));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aTraceIsEqualToItself() {
        DpMatrixTrace trace = minimalTrace();

        assertThat(trace).isEqualTo(trace);
    }

    @Test
    void aTraceIsNotEqualToNull() {
        assertThat(minimalTrace()).isNotEqualTo(null);
    }

    @Test
    void aTraceIsNotEqualToAnUnrelatedType() {
        assertThat(minimalTrace()).isNotEqualTo("not a trace");
    }

    @Test
    void tracesWithDifferentAlgorithmIdsAreNotEqual() {
        DpMatrixTrace first = minimalTrace();
        DpMatrixTrace second = new DpMatrixTrace("needleman-wunsch", List.of("a"), List.of("a"),
                new double[][] {{0, 1}, {1, 0}},
                List.of(new MatrixCell(1, 1), new MatrixCell(0, 0)),
                List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 1), DpOperationKind.MATCH)));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void tracesWithDifferentRowLabelsAreNotEqual() {
        DpMatrixTrace first = minimalTrace();
        DpMatrixTrace second = new DpMatrixTrace("levenshtein", List.of("z"), List.of("a"),
                new double[][] {{0, 1}, {1, 0}},
                List.of(new MatrixCell(1, 1), new MatrixCell(0, 0)),
                List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 1), DpOperationKind.MATCH)));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void tracesWithDifferentColumnLabelsAreNotEqual() {
        DpMatrixTrace first = minimalTrace();
        DpMatrixTrace second = new DpMatrixTrace("levenshtein", List.of("a"), List.of("z"),
                new double[][] {{0, 1}, {1, 0}},
                List.of(new MatrixCell(1, 1), new MatrixCell(0, 0)),
                List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 1), DpOperationKind.MATCH)));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void tracesWithDifferentOptimalPathsAreNotEqual() {
        double[][] matrix = {{0, 1, 2}, {1, 0, 1}};
        // First path: (1,2) -> (0,1) [diagonal, SUBSTITUTION] -> (0,0) [left, INSERTION].
        DpMatrixTrace first = new DpMatrixTrace("levenshtein", List.of("a"), List.of("a", "b"), matrix,
                List.of(new MatrixCell(1, 2), new MatrixCell(0, 1), new MatrixCell(0, 0)),
                List.of(
                        new DpTraceStep(new MatrixCell(0, 1), new MatrixCell(1, 2), DpOperationKind.SUBSTITUTION),
                        new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(0, 1), DpOperationKind.INSERTION)));
        // Second path: (1,2) -> (1,1) [left, INSERTION] -> (0,0) [diagonal, SUBSTITUTION].
        DpMatrixTrace second = new DpMatrixTrace("levenshtein", List.of("a"), List.of("a", "b"), matrix,
                List.of(new MatrixCell(1, 2), new MatrixCell(1, 1), new MatrixCell(0, 0)),
                List.of(
                        new DpTraceStep(new MatrixCell(1, 1), new MatrixCell(1, 2), DpOperationKind.INSERTION),
                        new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 1), DpOperationKind.SUBSTITUTION)));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void tracesWithDifferentOperationsAreNotEqual() {
        double[][] matrix = {{0, 1}, {1, 0}};
        DpMatrixTrace first = minimalTrace();
        DpMatrixTrace second = new DpMatrixTrace("levenshtein", List.of("a"), List.of("a"), matrix,
                List.of(new MatrixCell(1, 1), new MatrixCell(0, 0)),
                List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 1), DpOperationKind.SUBSTITUTION)));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hashCodeIsDifferentForTracesWithDifferentAlgorithmIds() {
        // Not a strict requirement of the equals/hashCode contract (only equal objects must
        // share a hash code, not the converse), but a useful sanity check that hashCode
        // actually incorporates every field equals() does, matching the earlier matrix-based
        // hashCode assertion (twoTracesWithEqualFieldsButDifferentMatrixInstancesAreEqual).
        DpMatrixTrace first = minimalTrace();
        DpMatrixTrace second = new DpMatrixTrace("needleman-wunsch", List.of("a"), List.of("a"),
                new double[][] {{0, 1}, {1, 0}},
                List.of(new MatrixCell(1, 1), new MatrixCell(0, 0)),
                List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 1), DpOperationKind.MATCH)));

        assertThat(first.hashCode()).isNotEqualTo(second.hashCode());
    }

    @Test
    void rejectsAnOperationsListWithTheWrongSize() {
        double[][] matrix = {{0, 1}, {1, 0}};

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DpMatrixTrace("levenshtein", List.of("a"), List.of("a"), matrix,
                        List.of(new MatrixCell(1, 1), new MatrixCell(0, 0)), List.of()))
                .withMessageContaining("operations");
    }

    @Test
    void rejectsAnOperationWhoseToCellDoesNotMatchThePath() {
        double[][] matrix = {{0, 1, 2}, {1, 0, 1}};

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DpMatrixTrace("levenshtein", List.of("a"), List.of("a", "b"), matrix,
                        List.of(new MatrixCell(1, 2), new MatrixCell(0, 1), new MatrixCell(0, 0)),
                        List.of(
                                new DpTraceStep(new MatrixCell(0, 1), new MatrixCell(1, 1), DpOperationKind.MATCH),
                                new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(0, 1), DpOperationKind.INSERTION))))
                .withMessageContaining("operations");
    }

    @Test
    void rejectsAMatchOperationThatIsNotDiagonal() {
        // rowLabels has 1 token, columnLabels is empty, so (0,0) -> (1,0) is the only
        // possible move: an "up" move, not diagonal, so MATCH (diagonal-only) is invalid.
        double[][] matrix = {{0}, {1}};

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DpMatrixTrace("levenshtein", List.of("a"), List.of(), matrix,
                        List.of(new MatrixCell(1, 0), new MatrixCell(0, 0)),
                        List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 0), DpOperationKind.MATCH))))
                .withMessageContaining("direction");
    }

    @Test
    void rejectsADeletionOperationThatIsDiagonalInsteadOfUp() {
        // (0,0) -> (1,1) is diagonal, but DELETION must be an "up" move (Levenshtein: a
        // token consumed from A without consuming B).
        double[][] matrix = {{0, 1}, {1, 0}};

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DpMatrixTrace("levenshtein", List.of("a"), List.of("a"), matrix,
                        List.of(new MatrixCell(1, 1), new MatrixCell(0, 0)),
                        List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 1), DpOperationKind.DELETION))))
                .withMessageContaining("direction");
    }

    @Test
    void rejectsAnInsertionOperationThatIsUpInsteadOfLeft() {
        // rowLabels has 1 token, columnLabels is empty, so (0,0) -> (1,0) is the only
        // possible move: an "up" move, but INSERTION must be a "left" move (Levenshtein: a
        // token consumed from B without consuming A).
        double[][] matrix = {{0}, {1}};

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DpMatrixTrace("levenshtein", List.of("a"), List.of(), matrix,
                        List.of(new MatrixCell(1, 0), new MatrixCell(0, 0)),
                        List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 0), DpOperationKind.INSERTION))))
                .withMessageContaining("direction");
    }

    @Test
    void acceptsAGapOperationInEitherUpOrLeftDirection() {
        // GAP (Needleman-Wunsch) is direction-agnostic: both an "up" move and a "left" move
        // are valid, unlike Levenshtein's directional DELETION/INSERTION.
        double[][] upMatrix = {{0}, {-1}};
        DpMatrixTrace upGap = new DpMatrixTrace("needleman-wunsch", List.of("a"), List.of(), upMatrix,
                List.of(new MatrixCell(1, 0), new MatrixCell(0, 0)),
                List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 0), DpOperationKind.GAP)));
        assertThat(upGap.operations()).hasSize(1);

        double[][] leftMatrix = {{0, -1}};
        DpMatrixTrace leftGap = new DpMatrixTrace("needleman-wunsch", List.of(), List.of("a"), leftMatrix,
                List.of(new MatrixCell(0, 1), new MatrixCell(0, 0)),
                List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(0, 1), DpOperationKind.GAP)));
        assertThat(leftGap.operations()).hasSize(1);
    }

    @Test
    void rejectsANonAdjacentOperationStep() {
        double[][] matrix = {{0, 1, 2}, {1, 0, 1}};

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DpMatrixTrace("levenshtein", List.of("a"), List.of("a", "b"), matrix,
                        List.of(new MatrixCell(1, 2), new MatrixCell(0, 0)),
                        List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 2), DpOperationKind.MATCH))))
                .withMessageContaining("adjacent");
    }

    @Test
    void acceptsATraceOnEmptyInputWithASingleCellPathAndNoOperations() {
        double[][] matrix = {{0}};

        DpMatrixTrace trace = new DpMatrixTrace("levenshtein", List.of(), List.of(), matrix,
                List.of(new MatrixCell(0, 0)), List.of());

        assertThat(trace.rowLabels()).isEmpty();
        assertThat(trace.columnLabels()).isEmpty();
        assertThat(trace.optimalPath()).containsExactly(new MatrixCell(0, 0));
        assertThat(trace.operations()).isEmpty();
    }

    private static DpMatrixTrace minimalTrace() {
        double[][] matrix = {{0, 1}, {1, 0}};
        return new DpMatrixTrace("levenshtein", List.of("a"), List.of("a"), matrix,
                List.of(new MatrixCell(1, 1), new MatrixCell(0, 0)),
                List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 1), DpOperationKind.MATCH)));
    }
}
