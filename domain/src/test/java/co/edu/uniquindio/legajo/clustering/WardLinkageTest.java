package co.edu.uniquindio.legajo.clustering;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/**
 * Ward linkage (TRD §6.4 table, "Ward"): alphaI = (n_i+n_k)/n_T, alphaJ = (n_j+n_k)/n_T,
 * beta = -n_k/n_T, gamma = 0, where n_T = n_i+n_j+n_k. Unlike the other three criteria this
 * one genuinely needs all three sizes; {@link LanceWilliamsEngine} (R3) is what feeds it
 * the merge distance and {@link DistanceMatrix#wardBase()}'s D_w = 2·D (R4) — this class
 * only ever produces coefficients, never touches a {@link DistanceMatrix} (TRD §6.4, "el
 * motor solo comparte el bucle de fusión").
 *
 * <p>Golden case (hand-computed): n_i=2, n_j=3, n_k=4 -&gt; n_T=9, alphaI=6/9=0.6666...,
 * alphaJ=7/9=0.7777..., beta=-4/9=-0.4444....
 */
class WardLinkageTest {

    private static final double TOLERANCE = 1e-9;

    private final WardLinkage criterion = new WardLinkage();

    @Test
    void coefficientsMatchTheHandDerivedGolden() {
        LanceWilliamsCoefficients coefficients = criterion.coefficients(2, 3, 4);

        assertThat(coefficients.alphaI()).isCloseTo(6.0 / 9.0, within(TOLERANCE));
        assertThat(coefficients.alphaJ()).isCloseTo(7.0 / 9.0, within(TOLERANCE));
        assertThat(coefficients.beta()).isCloseTo(-4.0 / 9.0, within(TOLERANCE));
        assertThat(coefficients.gamma()).isEqualTo(0.0);
    }

    @Test
    void equalSizesGiveTheSymmetricGolden() {
        // n_T = 3, alphaI = alphaJ = 2/3, beta = -1/3 -- the first-merge case every
        // WardMandatoryTest (R4) golden derivation starts from.
        LanceWilliamsCoefficients coefficients = criterion.coefficients(1, 1, 1);

        assertThat(coefficients.alphaI()).isCloseTo(2.0 / 3.0, within(TOLERANCE));
        assertThat(coefficients.alphaJ()).isCloseTo(2.0 / 3.0, within(TOLERANCE));
        assertThat(coefficients.beta()).isCloseTo(-1.0 / 3.0, within(TOLERANCE));
    }

    @Test
    void rejectsANonPositiveSizeI() {
        assertThatIllegalArgumentException().isThrownBy(() -> criterion.coefficients(0, 3, 4));
    }

    @Test
    void rejectsANonPositiveSizeJ() {
        assertThatIllegalArgumentException().isThrownBy(() -> criterion.coefficients(2, 0, 4));
    }

    @Test
    void rejectsANonPositiveSizeK() {
        assertThatIllegalArgumentException().isThrownBy(() -> criterion.coefficients(2, 3, -4));
    }

    @Test
    void idIsStableAndLowercase() {
        assertThat(criterion.id()).isEqualTo("ward");
    }

    @Test
    void displayNameIsHumanReadable() {
        assertThat(criterion.displayName()).isEqualTo("Ward linkage");
    }
}
