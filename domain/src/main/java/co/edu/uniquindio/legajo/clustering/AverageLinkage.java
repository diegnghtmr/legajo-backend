package co.edu.uniquindio.legajo.clustering;

/**
 * Average linkage: alphaI = n_i/(n_i+n_j),
 * alphaJ = n_j/(n_i+n_j), beta = 0, gamma = 0 — sizes weight the update so it equals the
 * true mean distance between every member of the merged cluster and k (UPGMA), not the
 * unweighted average of the two parents' distances to k. {@code sizeK} carries no
 * coefficient for this criterion; it is still accepted (the
 * {@link LinkageCriterion} signature is uniform across all four criteria) and simply
 * unused here.
 */
public final class AverageLinkage implements LinkageCriterion {

    @Override
    public String id() {
        return "average";
    }

    @Override
    public String displayName() {
        return "Average linkage";
    }

    @Override
    public LanceWilliamsCoefficients coefficients(int sizeI, int sizeJ, int sizeK) {
        if (sizeI <= 0) {
            throw new IllegalArgumentException("sizeI must be positive, was " + sizeI);
        }
        if (sizeJ <= 0) {
            throw new IllegalArgumentException("sizeJ must be positive, was " + sizeJ);
        }
        double total = sizeI + sizeJ;
        return new LanceWilliamsCoefficients(sizeI / total, sizeJ / total, 0.0, 0.0);
    }
}
