package co.edu.uniquindio.legajo.benchmarks.input;

import co.edu.uniquindio.legajo.clustering.DistanceMatrix;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the HAC-curve distance-matrix builder (synthetic unit vectors with
 * n ∈ {5, 10, 20, 40, 80}, per the fixed performance-test protocol). Written before
 * {@link SyntheticDistanceMatrices} exists (strict TDD). {@link DistanceMatrix}'s own
 * constructor already validates the symmetric, zero-diagonal invariant, but this test asserts
 * it explicitly against this module's own generated input.
 */
class SyntheticDistanceMatricesTest {

    @Test
    void buildsAMatrixOfTheRequestedSize() {
        DistanceMatrix matrix = SyntheticDistanceMatrices.cosineDistanceOf(20, 384, 42L);

        assertThat(matrix.size()).isEqualTo(20);
    }

    @Test
    void isDeterministicForAFixedSeed() {
        DistanceMatrix first = SyntheticDistanceMatrices.cosineDistanceOf(10, 384, 42L);
        DistanceMatrix second = SyntheticDistanceMatrices.cosineDistanceOf(10, 384, 42L);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void hasAZeroDiagonal() {
        DistanceMatrix matrix = SyntheticDistanceMatrices.cosineDistanceOf(10, 384, 42L);
        double[][] values = matrix.values();

        for (int i = 0; i < values.length; i++) {
            assertThat(values[i][i]).isCloseTo(0.0, within(1e-9));
        }
    }

    @Test
    void isSymmetric() {
        DistanceMatrix matrix = SyntheticDistanceMatrices.cosineDistanceOf(10, 384, 42L);
        double[][] values = matrix.values();

        for (int i = 0; i < values.length; i++) {
            for (int j = 0; j < values.length; j++) {
                assertThat(values[i][j]).isCloseTo(values[j][i], within(1e-9));
            }
        }
    }
}
