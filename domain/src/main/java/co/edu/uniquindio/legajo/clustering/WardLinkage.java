package co.edu.uniquindio.legajo.clustering;

/**
 * Ward linkage: alphaI = (n_i+n_k)/n_T, alphaJ = (n_j+n_k)/n_T,
 * beta = -n_k/n_T, gamma = 0, where n_T = n_i+n_j+n_k. Unlike the other three criteria this
 * one genuinely needs all three sizes. This class only ever produces coefficients
 * — it never accepts, builds, or holds a {@link DistanceMatrix}. Feeding Ward the correct
 * base, D_w = 2·D, is {@link LanceWilliamsEngine} and {@link DistanceMatrix#wardBase()}'s
 * job, never this class's — the merge engine owns only the merge loop.
 */
public final class WardLinkage implements LinkageCriterion {

    @Override
    public String id() {
        return "ward";
    }

    @Override
    public String displayName() {
        return "Ward linkage";
    }

    @Override
    public LanceWilliamsCoefficients coefficients(int sizeI, int sizeJ, int sizeK) {
        if (sizeI <= 0) {
            throw new IllegalArgumentException("sizeI must be positive, was " + sizeI);
        }
        if (sizeJ <= 0) {
            throw new IllegalArgumentException("sizeJ must be positive, was " + sizeJ);
        }
        if (sizeK <= 0) {
            throw new IllegalArgumentException("sizeK must be positive, was " + sizeK);
        }
        double total = sizeI + sizeJ + sizeK;
        return new LanceWilliamsCoefficients(
                (sizeI + sizeK) / total, (sizeJ + sizeK) / total, -sizeK / total, 0.0);
    }
}
