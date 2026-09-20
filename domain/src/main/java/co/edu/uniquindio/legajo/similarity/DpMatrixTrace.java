package co.edu.uniquindio.legajo.similarity;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Shared trace model for the two DP algorithms of TRD §6.3: the full matrix (never
 * truncated — the UI windows it, but the computed value always matches the published
 * trace), the row/column token labels, the deterministic optimal path (fixed backtrace
 * tie order: diagonal, then up, then left), and the per-step operation classification.
 *
 * <p>Modeled as plain, validated, read-only data so a future CSV/REST export is a pure
 * read of this record — no export or REST code is written here (S1 scope).
 *
 * <p>{@code matrix} is defensively deep-copied on construction and on every read of
 * {@link #matrix()}, because a {@code double[][]} field is otherwise a mutable hole in an
 * immutable record.
 */
public record DpMatrixTrace(
        String algorithmId,
        List<String> rowLabels,
        List<String> columnLabels,
        double[][] matrix,
        List<MatrixCell> optimalPath,
        List<DpTraceStep> operations) implements AlgorithmTrace {

    public DpMatrixTrace {
        Objects.requireNonNull(algorithmId, "algorithmId");
        Objects.requireNonNull(rowLabels, "rowLabels");
        Objects.requireNonNull(columnLabels, "columnLabels");
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(optimalPath, "optimalPath");
        Objects.requireNonNull(operations, "operations");

        rowLabels = List.copyOf(rowLabels);
        columnLabels = List.copyOf(columnLabels);
        optimalPath = List.copyOf(optimalPath);
        operations = List.copyOf(operations);
        matrix = deepCopy(matrix);

        int expectedRows = rowLabels.size() + 1;
        int expectedColumns = columnLabels.size() + 1;
        if (matrix.length != expectedRows) {
            throw new IllegalArgumentException(
                    "matrix must have %d rows (rowLabels.size() + 1), had %d".formatted(expectedRows, matrix.length));
        }
        for (double[] row : matrix) {
            if (row.length != expectedColumns) {
                throw new IllegalArgumentException(
                        "matrix must have %d columns (columnLabels.size() + 1) in every row"
                                .formatted(expectedColumns));
            }
        }

        if (optimalPath.isEmpty()) {
            throw new IllegalArgumentException("optimalPath must not be empty");
        }
        MatrixCell bottomRight = new MatrixCell(rowLabels.size(), columnLabels.size());
        if (!optimalPath.get(0).equals(bottomRight)) {
            throw new IllegalArgumentException(
                    "optimalPath must start at the bottom-right corner " + bottomRight);
        }
        MatrixCell origin = new MatrixCell(0, 0);
        if (!optimalPath.get(optimalPath.size() - 1).equals(origin)) {
            throw new IllegalArgumentException("optimalPath must end at (0,0)");
        }
    }

    @Override
    public double[][] matrix() {
        return deepCopy(matrix);
    }

    private static double[][] deepCopy(double[][] source) {
        double[][] copy = new double[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = Arrays.copyOf(source[i], source[i].length);
        }
        return copy;
    }
}
