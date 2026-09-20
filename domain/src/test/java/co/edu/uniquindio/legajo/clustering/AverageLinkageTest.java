package co.edu.uniquindio.legajo.clustering;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/**
 * Average linkage (TRD §6.4 table, "Promedio"): alphaI = n_i/(n_i+n_j),
 * alphaJ = n_j/(n_i+n_j), beta = 0, gamma = 0 — size-weighted so the Lance-Williams update
 * equals the true mean distance between every member of the merged cluster and k (UPGMA),
 * not merely the average of the two parent clusters' distances. {@code sizeK} is unused by
 * this criterion (the TRD table has no k-dependent term for "Promedio") but is still part
 * of the interface's uniform signature (R2), so it is accepted and ignored, not rejected.
 *
 * <p>Golden case (hand-computed): n_i=2, n_j=3 -&gt; alphaI = 2/5 = 0.4, alphaJ = 3/5 = 0.6.
 */
class AverageLinkageTest {

    private static final double TOLERANCE = 1e-9;

    private final AverageLinkage criterion = new AverageLinkage();

    @Test
    void coefficientsWeightBySizeAccordingToTheHandDerivedGolden() {
        LanceWilliamsCoefficients coefficients = criterion.coefficients(2, 3, 4);

        assertThat(coefficients.alphaI()).isCloseTo(0.4, within(TOLERANCE));
        assertThat(coefficients.alphaJ()).isCloseTo(0.6, within(TOLERANCE));
        assertThat(coefficients.beta()).isEqualTo(0.0);
        assertThat(coefficients.gamma()).isEqualTo(0.0);
    }

    @Test
    void equalSizesGiveEqualWeights() {
        LanceWilliamsCoefficients coefficients = criterion.coefficients(7, 7, 1);

        assertThat(coefficients.alphaI()).isCloseTo(0.5, within(TOLERANCE));
        assertThat(coefficients.alphaJ()).isCloseTo(0.5, within(TOLERANCE));
    }

    @Test
    void sizeKIsAcceptedButIgnored() {
        LanceWilliamsCoefficients withSmallK = criterion.coefficients(2, 3, 1);
        LanceWilliamsCoefficients withLargeK = criterion.coefficients(2, 3, 1000);

        assertThat(withSmallK).isEqualTo(withLargeK);
    }

    @Test
    void rejectsANonPositiveSizeI() {
        assertThatIllegalArgumentException().isThrownBy(() -> criterion.coefficients(0, 3, 4));
    }

    @Test
    void rejectsANonPositiveSizeJ() {
        assertThatIllegalArgumentException().isThrownBy(() -> criterion.coefficients(2, -1, 4));
    }

    @Test
    void idIsStableAndLowercase() {
        assertThat(criterion.id()).isEqualTo("average");
    }

    @Test
    void displayNameIsHumanReadable() {
        assertThat(criterion.displayName()).isEqualTo("Average linkage");
    }
}
