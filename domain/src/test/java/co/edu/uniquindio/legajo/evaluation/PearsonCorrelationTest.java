package co.edu.uniquindio.legajo.evaluation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

/**
 * Hand-written Pearson correlation (no library implements it: no statistics library), the
 * primitive {@link CopheneticCorrelation} builds on. Uses
 * {@code r = (n*Sxy - Sx*Sy) / sqrt((n*Sxx - Sx^2) * (n*Syy - Sy^2))}, the sum-of-products
 * form that avoids computing the means as a separate pass.
 */
class PearsonCorrelationTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void perfectPositiveLinearRelationshipScoresOne() {
        double r = PearsonCorrelation.of(new double[] {1, 2, 3}, new double[] {2, 4, 6});

        assertThat(r).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void perfectNegativeLinearRelationshipScoresMinusOne() {
        double r = PearsonCorrelation.of(new double[] {1, 2, 3}, new double[] {3, 2, 1});

        assertThat(r).isCloseTo(-1.0, within(TOLERANCE));
    }

    @Test
    void isSymmetric() {
        double[] x = {1, 2, 10, 11, 1, 9, 10, 8, 9, 1};
        double[] y = {1, 1, 8, 8, 1, 8, 8, 8, 8, 1};

        double xy = PearsonCorrelation.of(x, y);
        double yx = PearsonCorrelation.of(y, x);

        assertThat(xy).isCloseTo(yx, within(TOLERANCE));
    }

    @Test
    void matchesTheHandComputedValueForTheNEqualsFiveFixture() {
        // The exact pairs cophenetic-vs-D produces for LanceWilliamsEngineTest's n=5
        // single-linkage golden (five points on a line at 0,1,2,10,11), ten off-diagonal
        // pairs. The expected value was computed independently in Python with the raw
        // one-pass form: r = (10*461 - 62*52) / sqrt((10*554-62^2)*(10*388-52^2))
        // = 1386 / sqrt(1696*1176) = 0.9814013373262034. That the two-pass centered
        // implementation reproduces a value derived from the algebraically different one-pass
        // form is the point of this test, not an accident.
        double[] d = {1, 2, 10, 11, 1, 9, 10, 8, 9, 1};
        double[] cophenetic = {1, 1, 8, 8, 1, 8, 8, 8, 8, 1};

        double r = PearsonCorrelation.of(d, cophenetic);

        assertThat(r).isCloseTo(0.9814013373262034, within(TOLERANCE));
    }

    @Test
    void rejectsMismatchedLengths() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PearsonCorrelation.of(new double[] {1, 2}, new double[] {1, 2, 3}));
    }

    @Test
    void rejectsFewerThanTwoValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PearsonCorrelation.of(new double[] {1}, new double[] {1}));
    }

    @Test
    void rejectsZeroVarianceInput() {
        // Every value identical: the denominator is 0, which would silently divide to NaN
        // instead of failing closed (the same shape of bug NumericGuards exists to guard).
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PearsonCorrelation.of(new double[] {5, 5, 5}, new double[] {1, 2, 3}));
    }

    /**
     * The guard must be scale-free. A raw {@code n*sumX2 - sumX*sumX} variance term compared
     * against an absolute 1e-9 floor reads this input as having no variance: the term is
     * 6e-10 for each array even though the true correlation is exactly 1. Cophenetic
     * correlation is fed cosine distances that get this small when the corpus holds
     * near-duplicate abstracts, so an absolute floor would fail the primary ranking signal
     * closed on legitimate input.
     */
    @Test
    void acceptsLegitimateSmallMagnitudeInput() {
        double[] tiny = {1e-5, 2e-5, 3e-5};

        assertThat(PearsonCorrelation.of(tiny, tiny)).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void stillRejectsAConstantArrayWhateverItsMagnitude() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PearsonCorrelation.of(new double[] {1e-9, 1e-9, 1e-9},
                        new double[] {1, 2, 3}));
    }

    @Test
    void rejectsANullFirstArray() {
        assertThatNullPointerException().isThrownBy(() -> PearsonCorrelation.of(null, new double[] {1, 2}));
    }

    @Test
    void rejectsANullSecondArray() {
        assertThatNullPointerException().isThrownBy(() -> PearsonCorrelation.of(new double[] {1, 2}, null));
    }
}
