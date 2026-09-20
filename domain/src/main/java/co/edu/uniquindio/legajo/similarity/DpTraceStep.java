package co.edu.uniquindio.legajo.similarity;

import java.util.Objects;

/**
 * One backtrace move in a {@link DpMatrixTrace}, from {@code from} to {@code to}
 * (adjacent cells: diagonal, up, or left), classified by {@code operation}.
 */
public record DpTraceStep(MatrixCell from, MatrixCell to, DpOperationKind operation) {

    public DpTraceStep {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(operation, "operation");
    }
}
