package co.edu.uniquindio.legajo.evaluation;

import co.edu.uniquindio.legajo.clustering.DistanceMatrix;
import co.edu.uniquindio.legajo.clustering.LinkageMatrix;

import java.util.Objects;

/**
 * Cophenetic correlation (TRD §6.5): Pearson between the tree's cophenetic distances
 * ({@link CopheneticDistances}) and D, taken over the {@code n(n-1)/2} distinct
 * off-diagonal pairs — never the zero diagonal, never a pair counted twice. This is RF2's
 * <b>primary</b> ranking signal between the four linkage criteria.
 *
 * <p><b>Ward's 2x scale (TRD §6.4/§6.5).</b> Ward always clusters on {@code D_w = 2*D}, so
 * its raw cophenetic heights are twice the other three linkages'. Pearson correlation is
 * invariant to a positive linear rescaling of either operand, so correlating Ward's tree
 * against its own D_w gives the identical value as correlating against D directly — the
 * reason all four linkages stay comparable on this single ranking axis. See
 * {@code CopheneticCorrelationTest#isInvariantToWardsTwoTimesScale()} for the proof.
 */
public final class CopheneticCorrelation {

    private CopheneticCorrelation() {
    }

    /**
     * The cophenetic correlation of {@code linkage} against {@code distances}. {@code
     * distances} must have the same {@code n} as {@code linkage} ({@code n = linkage.size()
     * + 1}) — comparing a tree to a distance matrix over a different set of observations is
     * a caller error, not a silently-truncated computation.
     */
    public static double of(LinkageMatrix linkage, DistanceMatrix distances) {
        Objects.requireNonNull(linkage, "linkage");
        Objects.requireNonNull(distances, "distances");
        int n = linkage.size() + 1;
        if (distances.size() != n) {
            throw new IllegalArgumentException(
                    "distances must share linkage's n (%d), was %d".formatted(n, distances.size()));
        }

        double[][] cophenetic = CopheneticDistances.of(linkage);
        double[][] d = distances.values();

        int pairCount = n * (n - 1) / 2;
        double[] distancePairs = new double[pairCount];
        double[] copheneticPairs = new double[pairCount];
        int index = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                distancePairs[index] = d[i][j];
                copheneticPairs[index] = cophenetic[i][j];
                index++;
            }
        }

        return PearsonCorrelation.of(distancePairs, copheneticPairs);
    }
}
