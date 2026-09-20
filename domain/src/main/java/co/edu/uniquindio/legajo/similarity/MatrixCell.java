package co.edu.uniquindio.legajo.similarity;

/**
 * One coordinate in a {@link DpMatrixTrace} matrix: {@code row} indexes the token of
 * {@code rowLabels} consumed up to that point (0 = empty prefix of sequence A),
 * {@code col} indexes {@code columnLabels} the same way for sequence B.
 */
public record MatrixCell(int row, int col) {

    public MatrixCell {
        if (row < 0) {
            throw new IllegalArgumentException("row must not be negative, was " + row);
        }
        if (col < 0) {
            throw new IllegalArgumentException("col must not be negative, was " + col);
        }
    }
}
