package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.similarity.MatrixCell;

import java.util.Objects;

/** Wire shape of one {@link MatrixCell} coordinate in a DP trace's optimal path. */
public record MatrixCellResponse(int row, int col) {

    public static MatrixCellResponse from(MatrixCell cell) {
        Objects.requireNonNull(cell, "cell");
        return new MatrixCellResponse(cell.row(), cell.col());
    }
}
