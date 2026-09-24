package co.edu.uniquindio.legajo.clustering;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Single linkage: alphaI = alphaJ = 1/2, beta = 0, gamma = -1/2,
 * independent of the three cluster sizes — plugging these into Lance-Williams reduces to
 * {@code min(d(i,k), d(j,k))}, the classic nearest-neighbor update, but this class's only
 * job is to hand the engine
 * those four constants; {@link LanceWilliamsEngine} owns the merge loop itself.
 */
class SingleLinkageTest {

    private final SingleLinkage criterion = new SingleLinkage();

    @Test
    void coefficientsMatchTheFixedTableRegardlessOfClusterSizes() {
        LanceWilliamsCoefficients coefficients = criterion.coefficients(2, 3, 4);

        assertThat(coefficients.alphaI()).isEqualTo(0.5);
        assertThat(coefficients.alphaJ()).isEqualTo(0.5);
        assertThat(coefficients.beta()).isEqualTo(0.0);
        assertThat(coefficients.gamma()).isEqualTo(-0.5);
    }

    @Test
    void coefficientsAreConstantAcrossAnyClusterSizeTriple() {
        LanceWilliamsCoefficients small = criterion.coefficients(1, 1, 1);
        LanceWilliamsCoefficients large = criterion.coefficients(50, 1, 200);

        assertThat(small).isEqualTo(large);
    }

    @Test
    void idIsStableAndLowercase() {
        assertThat(criterion.id()).isEqualTo("single");
    }

    @Test
    void displayNameIsHumanReadable() {
        assertThat(criterion.displayName()).isEqualTo("Single linkage");
    }
}
