package co.edu.uniquindio.legajo.benchmarks.input;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the HAC-curve and embedding-primitive vector builder (synthetic unit
 * vectors with n ∈ {5, 10, 20, 40, 80}, per the fixed performance-test protocol; embedding
 * metrics at d = 384/1536). Written before {@link SyntheticUnitVectors} exists (strict TDD).
 */
class SyntheticUnitVectorsTest {

    @Test
    void generatesTheRequestedCountAndDimension() {
        List<List<Double>> vectors = SyntheticUnitVectors.generate(5, 384, 1L);

        assertThat(vectors).hasSize(5);
        assertThat(vectors).allSatisfy(vector -> assertThat(vector).hasSize(384));
    }

    @Test
    void everyVectorIsL2Normalized() {
        List<List<Double>> vectors = SyntheticUnitVectors.generate(20, 1536, 7L);

        for (List<Double> vector : vectors) {
            double sumOfSquares = vector.stream().mapToDouble(v -> v * v).sum();
            assertThat(Math.sqrt(sumOfSquares)).isCloseTo(1.0, within(1e-9));
        }
    }

    @Test
    void isDeterministicForAFixedSeed() {
        List<List<Double>> first = SyntheticUnitVectors.generate(10, 384, 42L);
        List<List<Double>> second = SyntheticUnitVectors.generate(10, 384, 42L);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentSeedsProduceDifferentVectors() {
        List<List<Double>> seed1 = SyntheticUnitVectors.generate(1, 384, 1L);
        List<List<Double>> seed2 = SyntheticUnitVectors.generate(1, 384, 2L);

        assertThat(seed1).isNotEqualTo(seed2);
    }

    @Test
    void distinctVectorsOfTheSameCallAreNotIdentical() {
        List<List<Double>> vectors = SyntheticUnitVectors.generate(2, 384, 42L);

        assertThat(vectors.get(0)).isNotEqualTo(vectors.get(1));
    }

    @Test
    void rejectsANonPositiveCount() {
        assertThatThrownBy(() -> SyntheticUnitVectors.generate(0, 384, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonPositiveDimension() {
        assertThatThrownBy(() -> SyntheticUnitVectors.generate(5, 0, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
