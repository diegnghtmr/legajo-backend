package co.edu.uniquindio.legajo.similarity;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Shared trace model for the two DP algorithms: the full matrix (never
 * truncated — the UI windows it, but the computed value always matches the published
 * trace), the row/column token labels, the deterministic optimal path (fixed backtrace
 * tie order: diagonal, then up, then left), and the per-step operation classification.
 *
 * <p>Modeled as plain, validated, read-only data so a future CSV/REST export is a pure
 * read of this record — no export or REST code is written here.
 *
 * <p>{@code matrix} is defensively deep-copied on construction and on every read of
 * {@link #matrix()}, because a {@code double[][]} field is otherwise a mutable hole in an
 * immutable record. {@code equals}/{@code hashCode} are overridden to compare {@code matrix}
 * by content ({@link Arrays#deepEquals} / {@link Arrays#deepHashCode}) instead of the
 * record-generated array reference comparison, which would otherwise report two traces with
 * identical data as unequal whenever their {@code matrix} arrays are different instances.
 *
 * <p>{@code operations} is validated against {@code optimalPath}: one operation per path
 * transition, in the same order (each {@code operations.get(i)} moves from
 * {@code optimalPath.get(i + 1)} to {@code optimalPath.get(i)}), and every move stays within
 * one row and one column of its neighbor (diagonal, up, or left), matching the fixed
 * backtrace tie order. Each step's {@link DpOperationKind} must also match its
 * actual geometric direction: {@link DpOperationKind#MATCH}/{@link DpOperationKind#SUBSTITUTION}/
 * {@link DpOperationKind#MISMATCH} require a diagonal move; {@link DpOperationKind#DELETION}
 * requires an up move; {@link DpOperationKind#INSERTION} requires a left move;
 * {@link DpOperationKind#GAP} accepts either up or left (Needleman-Wunsch does not
 * distinguish a gap's direction).
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

        int expectedOperations = optimalPath.size() - 1;
        if (operations.size() != expectedOperations) {
            throw new IllegalArgumentException(
                    "operations must have one entry per optimalPath transition (%d), had %d"
                            .formatted(expectedOperations, operations.size()));
        }
        for (int step = 0; step < operations.size(); step++) {
            DpTraceStep operation = operations.get(step);
            MatrixCell expectedTo = optimalPath.get(step);
            MatrixCell expectedFrom = optimalPath.get(step + 1);
            if (!operation.to().equals(expectedTo) || !operation.from().equals(expectedFrom)) {
                throw new IllegalArgumentException(
                        "operations[%d] must move from %s to %s to match optimalPath, was from %s to %s"
                                .formatted(step, expectedFrom, expectedTo, operation.from(), operation.to()));
            }
            int rowDelta = expectedTo.row() - expectedFrom.row();
            int colDelta = expectedTo.col() - expectedFrom.col();
            boolean adjacent = rowDelta >= 0 && rowDelta <= 1 && colDelta >= 0 && colDelta <= 1
                    && rowDelta + colDelta > 0;
            if (!adjacent) {
                throw new IllegalArgumentException(
                        "operations[%d] must be adjacent (diagonal, up, or left), was from %s to %s"
                                .formatted(step, expectedFrom, expectedTo));
            }

            boolean diagonal = rowDelta == 1 && colDelta == 1;
            boolean up = rowDelta == 1 && colDelta == 0;
            boolean left = rowDelta == 0 && colDelta == 1;
            boolean directionMatchesOperation = switch (operation.operation()) {
                case MATCH, SUBSTITUTION, MISMATCH -> diagonal;
                case DELETION -> up;
                case INSERTION -> left;
                case GAP -> up || left;
            };
            if (!directionMatchesOperation) {
                throw new IllegalArgumentException(
                        "operations[%d] has operation kind %s inconsistent with its direction, was from %s to %s"
                                .formatted(step, operation.operation(), expectedFrom, expectedTo));
            }
        }
    }

    @Override
    public double[][] matrix() {
        return deepCopy(matrix);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DpMatrixTrace that)) {
            return false;
        }
        return algorithmId.equals(that.algorithmId)
                && rowLabels.equals(that.rowLabels)
                && columnLabels.equals(that.columnLabels)
                && Arrays.deepEquals(matrix, that.matrix)
                && optimalPath.equals(that.optimalPath)
                && operations.equals(that.operations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(algorithmId, rowLabels, columnLabels, Arrays.deepHashCode(matrix), optimalPath,
                operations);
    }

    private static double[][] deepCopy(double[][] source) {
        double[][] copy = new double[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = Arrays.copyOf(source[i], source[i].length);
        }
        return copy;
    }
}
