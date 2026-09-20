package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** {@link MatrixCell} coordinates are always non-negative DP matrix indices. */
class MatrixCellTest {

    @Test
    void rejectsANegativeRow() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MatrixCell(-1, 0))
                .withMessageContaining("row");
    }

    @Test
    void rejectsANegativeColumn() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MatrixCell(0, -1))
                .withMessageContaining("col");
    }
}
