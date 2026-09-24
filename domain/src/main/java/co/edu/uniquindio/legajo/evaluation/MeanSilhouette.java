package co.edu.uniquindio.legajo.evaluation;

import co.edu.uniquindio.legajo.clustering.ClusterAssignment;
import co.edu.uniquindio.legajo.clustering.DistanceMatrix;
import co.edu.uniquindio.legajo.similarity.NumericGuards;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mean silhouette against D at a fixed k:
 * partition quality, and the ranking rule's tie-break/leader signal at {@code k_ref}.
 *
 * <p><b>Per-document definition.</b> For {@code i} in cluster {@code C}
 * with {@code |C| > 1}: {@code a(i)} = mean of {@code D(i, j)} over {@code j} in {@code C},
 * {@code j != i}; {@code b(i)} = the minimum, over each other cluster {@code C'}, of the mean
 * of {@code D(i, j)} over {@code j} in {@code C'}; {@code s(i) = (b(i) - a(i)) /
 * max(a(i), b(i))}. <b>A singleton cluster gives {@code s(i) = 0}</b> (Rousseeuw's
 * convention) — a different branch from the general formula, not a special case of it,
 * because {@code a(i)} is undefined (no other member to average against) when
 * {@code |C| = 1}.
 *
 * <p><b>The {@code max(a(i), b(i)) == 0} edge case (author decision, not otherwise
 * specified).</b> D is non-negative, so {@code a(i)} and {@code b(i)} are always &gt;= 0;
 * {@code max(a(i), b(i)) == 0} can only hold when both are exactly 0 — every member of
 * {@code i}'s own cluster and every member of its nearest other cluster sits at distance 0
 * from {@code i}. The formula would then divide 0 by 0. This class follows the same
 * convention scikit-learn's {@code silhouette_samples} uses for the identical edge case:
 * {@code s(i) = 0}. The check is an exact equality against {@code 0.0}, not a tolerance:
 * {@code a(i)} and {@code b(i)} are non-negative sums of non-negative D entries, so their
 * maximum is exactly zero if and only if every term summed to exactly zero — an algebraic
 * condition that needs no magnitude-relative epsilon (the same scale-free guard rule
 * already enforced once on {@link PearsonCorrelation}).
 *
 * <p>Averages {@code s(i)} over all {@code n} documents of {@code assignment}
 * ({@code (1/n)*Sum_i s(i)}), always against the reference {@code distances} (D) — never
 * Ward's {@code D_w = 2*D}. The ranking rule is responsible for cutting every one of the four linkages
 * against that one shared D, calculated always against the reference distance matrix D.
 */
public final class MeanSilhouette {

    private MeanSilhouette() {
    }

    /**
     * The mean silhouette of {@code assignment} (a fixed-k cut) against {@code distances}.
     * Requires {@code assignment.k() >= 2}: the fixed cuts are always
     * {@code k in {2,3,4,5} ∩ [2, n-1]}, and the formula itself has no "other cluster" to
     * compare against when {@code k = 1}.
     */
    public static double of(ClusterAssignment assignment, DistanceMatrix distances) {
        Objects.requireNonNull(assignment, "assignment");
        Objects.requireNonNull(distances, "distances");
        int n = assignment.size();
        if (distances.size() != n) {
            throw new IllegalArgumentException(
                    "distances must share assignment's size (%d), was %d".formatted(n, distances.size()));
        }
        int k = assignment.k();
        if (k < 2) {
            throw new IllegalArgumentException(
                    "mean silhouette requires at least 2 clusters (the fixed cuts start at k=2), was k="
                            + k);
        }

        List<Integer> labels = assignment.labels();
        double[][] d = distances.values();

        List<List<Integer>> membersByLabel = new ArrayList<>(k);
        for (int c = 0; c < k; c++) {
            membersByLabel.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            membersByLabel.get(labels.get(i)).add(i);
        }

        double total = 0.0;
        for (int i = 0; i < n; i++) {
            int ownLabel = labels.get(i);
            List<Integer> own = membersByLabel.get(ownLabel);
            if (own.size() == 1) {
                // s(i) = 0 by the singleton convention; nothing to add to the running total.
                continue;
            }

            double aSum = 0.0;
            for (int j : own) {
                if (j != i) {
                    aSum += d[i][j];
                }
            }
            double a = aSum / (own.size() - 1);

            double b = Double.POSITIVE_INFINITY;
            for (int c = 0; c < k; c++) {
                if (c == ownLabel) {
                    continue;
                }
                List<Integer> other = membersByLabel.get(c);
                double sum = 0.0;
                for (int j : other) {
                    sum += d[i][j];
                }
                double mean = sum / other.size();
                if (mean < b) {
                    b = mean;
                }
            }

            double denominator = Math.max(a, b);
            double s = denominator == 0.0 ? 0.0 : (b - a) / denominator;
            NumericGuards.requireFinite(s, "s(%d)".formatted(i));
            total += s;
        }

        return total / n;
    }
}
