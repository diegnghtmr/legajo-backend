package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link DpMatrixTrace} is the shared trace model for the two DP algorithms of TRD §6.3
 * (Levenshtein, S2; Needleman–Wunsch, S3): the full matrix, row/column token labels, the
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

    private static DpMatrixTrace minimalTrace() {
        double[][] matrix = {{0, 1}, {1, 0}};
        return new DpMatrixTrace("levenshtein", List.of("a"), List.of("a"), matrix,
                List.of(new MatrixCell(1, 1), new MatrixCell(0, 0)),
                List.of(new DpTraceStep(new MatrixCell(0, 0), new MatrixCell(1, 1), DpOperationKind.MATCH)));
    }
}
