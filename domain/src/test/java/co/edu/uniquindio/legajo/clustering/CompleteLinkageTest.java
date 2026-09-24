package co.edu.uniquindio.legajo.clustering;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Complete linkage: alphaI = alphaJ = 1/2, beta = 0,
 * gamma = +1/2, independent of the three cluster sizes — the sign flip on gamma versus
 * {@link SingleLinkage} is the entire difference between nearest-neighbor and
 * farthest-neighbor Lance-Williams updates.
 */
class CompleteLinkageTest {

    private final CompleteLinkage criterion = new CompleteLinkage();

    @Test
    void coefficientsMatchTheFixedTableRegardlessOfClusterSizes() {
        LanceWilliamsCoefficients coefficients = criterion.coefficients(2, 3, 4);

        assertThat(coefficients.alphaI()).isEqualTo(0.5);
        assertThat(coefficients.alphaJ()).isEqualTo(0.5);
        assertThat(coefficients.beta()).isEqualTo(0.0);
        assertThat(coefficients.gamma()).isEqualTo(0.5);
    }

    @Test
    void coefficientsAreConstantAcrossAnyClusterSizeTriple() {
        LanceWilliamsCoefficients small = criterion.coefficients(1, 1, 1);
        LanceWilliamsCoefficients large = criterion.coefficients(50, 1, 200);

        assertThat(small).isEqualTo(large);
    }

    @Test
    void idIsStableAndLowercase() {
        assertThat(criterion.id()).isEqualTo("complete");
    }

    @Test
    void displayNameIsHumanReadable() {
        assertThat(criterion.displayName()).isEqualTo("Complete linkage");
    }
}
