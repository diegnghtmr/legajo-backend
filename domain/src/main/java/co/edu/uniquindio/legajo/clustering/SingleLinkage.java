package co.edu.uniquindio.legajo.clustering;

/**
 * Single linkage: alphaI = alphaJ = 1/2, beta = 0, gamma = -1/2,
 * independent of the three cluster sizes. Substituting these constants into Lance-Williams
 * reduces algebraically to {@code min(d(i,k), d(j,k))} — nearest-neighbor clustering — but
 * this class never performs that reduction itself; it only ever hands the four constants to
 * {@link LanceWilliamsEngine}, which owns the merge loop.
 */
public final class SingleLinkage implements LinkageCriterion {

    private static final LanceWilliamsCoefficients COEFFICIENTS =
            new LanceWilliamsCoefficients(0.5, 0.5, 0.0, -0.5);

    @Override
    public String id() {
        return "single";
    }

    @Override
    public String displayName() {
        return "Single linkage";
    }

    @Override
    public LanceWilliamsCoefficients coefficients(int sizeI, int sizeJ, int sizeK) {
        return COEFFICIENTS;
    }
}
