package co.edu.uniquindio.legajo.clustering;

/**
 * Complete linkage (TRD §6.4 table, "Completo"): alphaI = alphaJ = 1/2, beta = 0,
 * gamma = +1/2, independent of the three cluster sizes. The sign flip on gamma versus
 * {@link SingleLinkage} is the entire algebraic difference between nearest-neighbor and
 * farthest-neighbor Lance-Williams updates; {@link LanceWilliamsEngine} (R3) owns the merge
 * loop that actually applies these coefficients (TRD §6.4).
 */
public final class CompleteLinkage implements LinkageCriterion {

    private static final LanceWilliamsCoefficients COEFFICIENTS =
            new LanceWilliamsCoefficients(0.5, 0.5, 0.0, 0.5);

    @Override
    public String id() {
        return "complete";
    }

    @Override
    public String displayName() {
        return "Complete linkage";
    }

    @Override
    public LanceWilliamsCoefficients coefficients(int sizeI, int sizeJ, int sizeK) {
        return COEFFICIENTS;
    }
}
