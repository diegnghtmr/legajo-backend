package co.edu.uniquindio.legajo.clustering;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The four Lance-Williams coefficients one {@link LinkageCriterion} contributes
 * per merge: {@code alphaI}, {@code alphaJ}, {@code beta}, {@code gamma} in
 * {@code d(i∪j, k) = alphaI*d(i,k) + alphaJ*d(j,k) + beta*d(i,j) + gamma*|d(i,k)-d(j,k)|}.
 * Plain accessors; the four criteria classes are the only producers, so this record's own
 * job is exactly what {@code DpMatrixTrace}'s Javadoc calls "the compact constructor's
 * tolerance and range invariants": reject a non-finite coefficient before it can silently
 * corrupt the engine's merge loop.
 */
class LanceWilliamsCoefficientsTest {

    @Test
    void accessorsReturnTheGivenCoefficients() {
        LanceWilliamsCoefficients coefficients = new LanceWilliamsCoefficients(0.5, 0.5, 0.0, -0.5);

        assertThat(coefficients.alphaI()).isEqualTo(0.5);
        assertThat(coefficients.alphaJ()).isEqualTo(0.5);
        assertThat(coefficients.beta()).isEqualTo(0.0);
        assertThat(coefficients.gamma()).isEqualTo(-0.5);
    }

    @Test
    void rejectsANonFiniteAlphaI() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LanceWilliamsCoefficients(Double.NaN, 0.5, 0.0, -0.5));
    }

    @Test
    void rejectsANonFiniteAlphaJ() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LanceWilliamsCoefficients(0.5, Double.POSITIVE_INFINITY, 0.0, -0.5));
    }

    @Test
    void rejectsANonFiniteBeta() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LanceWilliamsCoefficients(0.5, 0.5, Double.NEGATIVE_INFINITY, -0.5));
    }

    @Test
    void rejectsANonFiniteGamma() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LanceWilliamsCoefficients(0.5, 0.5, 0.0, Double.NaN));
    }
}
