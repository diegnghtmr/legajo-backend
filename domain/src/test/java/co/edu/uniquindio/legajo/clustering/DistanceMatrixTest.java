package co.edu.uniquindio.legajo.clustering;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

/**
 * The validated n×n distance matrix the four linkage criteria consume: every
 * entry finite and non-negative, symmetric within 1e-9, zero diagonal within 1e-9.
 *
 * <p>Golden case (hand-computed, three 2D unit vectors):
 *
 * <pre>
 * v0 = (1, 0), v1 = (0, 1), v2 = (1/sqrt(2), 1/sqrt(2))
 * cos(v0,v1) = 0                    -&gt; D[0][1] = 1.0
 * cos(v0,v2) = cos(v1,v2) = 1/sqrt(2) = 0.7071067811865476 -&gt; D = 0.29289321881345254
 * D_w = 2*D: D_w[0][1] = 2.0, D_w[0][2] = D_w[1][2] = 0.5857864376269051
 * </pre>
 */
class DistanceMatrixTest {

    private static final double TOLERANCE = 1e-9;
    private static final double SQRT_HALF = 0.7071067811865476;

    @Test
    void cosineDistanceComputesTheHandDerivedGolden() {
        List<List<Double>> vectors = List.of(
                List.of(1.0, 0.0),
                List.of(0.0, 1.0),
                List.of(SQRT_HALF, SQRT_HALF));

        DistanceMatrix matrix = DistanceMatrix.cosineDistance(vectors);

        assertThat(matrix.size()).isEqualTo(3);
        double[][] values = matrix.values();
        assertThat(values[0][0]).isCloseTo(0.0, within(TOLERANCE));
        assertThat(values[1][1]).isCloseTo(0.0, within(TOLERANCE));
        assertThat(values[2][2]).isCloseTo(0.0, within(TOLERANCE));
        assertThat(values[0][1]).isCloseTo(1.0, within(TOLERANCE));
        assertThat(values[1][0]).isCloseTo(1.0, within(TOLERANCE));
        assertThat(values[0][2]).isCloseTo(0.29289321881345254, within(TOLERANCE));
        assertThat(values[1][2]).isCloseTo(0.29289321881345254, within(TOLERANCE));
    }

    @Test
    void wardBaseDoublesAnAlreadyDerivedDistanceMatrix() {
        List<List<Double>> vectors = List.of(
                List.of(1.0, 0.0),
                List.of(0.0, 1.0),
                List.of(SQRT_HALF, SQRT_HALF));
        DistanceMatrix d = DistanceMatrix.cosineDistance(vectors);

        DistanceMatrix wardBase = d.wardBase();

        double[][] values = wardBase.values();
        assertThat(values[0][0]).isCloseTo(0.0, within(TOLERANCE));
        assertThat(values[0][1]).isCloseTo(2.0, within(TOLERANCE));
        assertThat(values[0][2]).isCloseTo(0.5857864376269051, within(TOLERANCE));
        assertThat(values[1][2]).isCloseTo(0.5857864376269051, within(TOLERANCE));
        assertThat(wardBase.size()).isEqualTo(d.size());
    }

    @Test
    void cosineDistanceRejectsANullVectorList() {
        assertThatNullPointerException().isThrownBy(() -> DistanceMatrix.cosineDistance(null));
    }

    @Test
    void cosineDistanceRejectsAnEmptyVectorList() {
        assertThatIllegalArgumentException().isThrownBy(() -> DistanceMatrix.cosineDistance(List.of()));
    }

    @Test
    void cosineDistanceRejectsMismatchedDimensions() {
        List<List<Double>> vectors = List.of(List.of(1.0, 0.0), List.of(1.0, 0.0, 0.0));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DistanceMatrix.cosineDistance(vectors))
                .withMessageContaining("dimension");
    }

    @Test
    void cosineDistanceRejectsANonUnitVector() {
        List<List<Double>> vectors = List.of(List.of(1.0, 0.0), List.of(2.0, 0.0));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DistanceMatrix.cosineDistance(vectors))
                .withMessageContaining("L2-normalized");
    }

    @Test
    void cosineDistanceRejectsANonFiniteComponent() {
        List<List<Double>> vectors = List.of(List.of(1.0, 0.0), List.of(Double.NaN, 0.0));

        assertThatIllegalArgumentException().isThrownBy(() -> DistanceMatrix.cosineDistance(vectors));
    }

    @Test
    void valuesReturnsADefensiveCopyNotTheInternalArray() {
        List<List<Double>> vectors = List.of(List.of(1.0, 0.0), List.of(0.0, 1.0));
        DistanceMatrix matrix = DistanceMatrix.cosineDistance(vectors);

        double[][] first = matrix.values();
        first[0][1] = 999.0;
        double[][] second = matrix.values();

        assertThat(second[0][1]).isNotEqualTo(999.0);
    }

    @Test
    void equalsAndHashCodeCompareByContentNotReference() {
        List<List<Double>> vectors = List.of(List.of(1.0, 0.0), List.of(0.0, 1.0));
        DistanceMatrix a = DistanceMatrix.cosineDistance(vectors);
        DistanceMatrix b = DistanceMatrix.cosineDistance(List.of(List.of(1.0, 0.0), List.of(0.0, 1.0)));

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void equalsIsReflexive() {
        DistanceMatrix a = DistanceMatrix.cosineDistance(List.of(List.of(1.0, 0.0), List.of(0.0, 1.0)));

        assertThat(a).isEqualTo(a);
    }

    @Test
    void equalsIsFalseAgainstNullAndAnUnrelatedType() {
        DistanceMatrix a = DistanceMatrix.cosineDistance(List.of(List.of(1.0, 0.0), List.of(0.0, 1.0)));

        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("not a DistanceMatrix");
    }

    @Test
    void cosineDistanceRejectsAnEmptyInnerVector() {
        List<List<Double>> vectors = List.of(List.of(1.0, 0.0), List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DistanceMatrix.cosineDistance(vectors))
                .withMessageContaining("must not be empty");
    }

    // The remaining tests exercise the package-private raw constructor directly: a genuine
    // cosine derivation can never itself produce an asymmetric, non-zero-diagonal, negative,
    // or non-square matrix, so these invariant violations can only be constructed by hand.

    @Test
    void constructorRejectsANonSquareMatrix() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DistanceMatrix(new double[][] {{0.0, 1.0, 2.0}, {1.0, 0.0}}))
                .withMessageContaining("square");
    }

    @Test
    void constructorRejectsAnEmptyMatrix() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DistanceMatrix(new double[0][0]));
    }

    @Test
    void constructorRejectsANonZeroDiagonal() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DistanceMatrix(new double[][] {{0.1, 1.0}, {1.0, 0.0}}))
                .withMessageContaining("zero diagonal");
    }

    @Test
    void constructorRejectsAnAsymmetricMatrix() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DistanceMatrix(new double[][] {{0.0, 1.0}, {2.0, 0.0}}))
                .withMessageContaining("symmetric");
    }

    @Test
    void constructorRejectsANegativeEntry() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DistanceMatrix(new double[][] {{0.0, -1.0}, {-1.0, 0.0}}));
    }

    @Test
    void constructorRejectsANonFiniteEntry() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DistanceMatrix(new double[][] {{0.0, Double.NaN}, {Double.NaN, 0.0}}));
    }

    @Test
    void constructorAcceptsAValidHandCraftedMatrix() {
        DistanceMatrix matrix = new DistanceMatrix(new double[][] {{0.0, 1.0}, {1.0, 0.0}});

        assertThat(matrix.size()).isEqualTo(2);
        assertThat(matrix.values()).isEqualTo(new double[][] {{0.0, 1.0}, {1.0, 0.0}});
    }
}
