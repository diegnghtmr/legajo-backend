package co.edu.uniquindio.legajo.evaluation;

import co.edu.uniquindio.legajo.clustering.ClusterAssignment;
import co.edu.uniquindio.legajo.similarity.NumericGuards;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Davies-Bouldin in V space at a fixed k (TRD §6.5, "Definición de Davies-Bouldin"): an
 * <b>additional</b> partition-quality signal that never replaces the cophenetic criterion.
 *
 * <p><b>Definition (fixed by the TRD).</b> centroid {@code c_i} = mean of cluster {@code i}'s
 * L2-normalized vectors; {@code sigma_i} = mean Euclidean distance of cluster {@code i}'s
 * members to {@code c_i}; {@code M_ij} = Euclidean distance between centroids {@code c_i} and
 * {@code c_j}; {@code DB = (1/k) * Sum_i max_{j != i} (sigma_i + sigma_j) / M_ij}. Lower is
 * better.
 *
 * <p><b>The {@code M_ij = 0} case (fixed by the TRD).</b> "Si M_ij = 0 (centroides
 * coincidentes) el cociente se toma como +infinito y DB se reporta como null (no definido)
 * para ese k." This class models that {@code null} as {@link OptionalDouble#empty()} rather
 * than {@code NaN} or a sentinel double, so a caller cannot accidentally treat "undefined" as
 * a real, comparable DB value. The {@code M_ij == 0} check is an exact equality, not a
 * tolerance: {@code M_ij} is a Euclidean distance and is exactly zero if and only if the two
 * centroids are bit-identical, which needs no magnitude-relative epsilon (the same scale-free
 * guard rule this feature's review already enforced once on {@link PearsonCorrelation}). A
 * centroid merely very close to (but not bit-identical to) another still yields a very large
 * but finite, meaningful DB contribution — a different, legitimate case the TRD never asks
 * this class to special-case.
 *
 * <p>A singleton cluster's {@code sigma_i} is 0 (its one member's distance to its own
 * centroid — that member's own vector — is 0), which is legitimate on its own and only feeds
 * into the undefined case above if, additionally, some {@code M_ij} for that cluster is also
 * exactly 0.
 */
public final class DaviesBouldin {

    private static final double UNIT_NORM_TOLERANCE = 1e-9;

    private DaviesBouldin() {
    }

    /**
     * Davies-Bouldin of {@code assignment} (a fixed-k cut) over {@code l2NormalizedVectors} —
     * the same L2-normalized vectors the run's representation derives D from (TRD §6.4/§6.5),
     * never D itself, since this metric lives in V space. Requires
     * {@code assignment.k() >= 2}, mirroring {@link MeanSilhouette#of}: TRD §6.5's fixed cuts
     * never go below k=2, and {@code max_{j != i}} has no meaning for a single cluster.
     */
    public static OptionalDouble of(ClusterAssignment assignment, List<List<Double>> l2NormalizedVectors) {
        Objects.requireNonNull(assignment, "assignment");
        Objects.requireNonNull(l2NormalizedVectors, "l2NormalizedVectors");
        int n = assignment.size();
        if (l2NormalizedVectors.size() != n) {
            throw new IllegalArgumentException(
                    "l2NormalizedVectors must share assignment's size (%d), was %d"
                            .formatted(n, l2NormalizedVectors.size()));
        }
        int k = assignment.k();
        if (k < 2) {
            throw new IllegalArgumentException(
                    "Davies-Bouldin requires at least 2 clusters (TRD §6.5's fixed cuts start at k=2), was k="
                            + k);
        }

        double[][] vectors = new double[n][];
        int dimension = -1;
        for (int i = 0; i < n; i++) {
            List<Double> vector = l2NormalizedVectors.get(i);
            Objects.requireNonNull(vector, "l2NormalizedVectors must not contain a null vector");
            if (vector.isEmpty()) {
                throw new IllegalArgumentException("l2NormalizedVectors[%d] must not be empty".formatted(i));
            }
            if (dimension == -1) {
                dimension = vector.size();
            } else if (vector.size() != dimension) {
                throw new IllegalArgumentException(
                        "every vector must share one dimension (%d), l2NormalizedVectors[%d] had %d"
                                .formatted(dimension, i, vector.size()));
            }
            double[] components = new double[vector.size()];
            double sumOfSquares = 0.0;
            for (int d = 0; d < vector.size(); d++) {
                double component = Objects.requireNonNull(vector.get(d),
                        "l2NormalizedVectors[%d] must not contain a null component".formatted(i));
                NumericGuards.requireFinite(component, "l2NormalizedVectors[%d][%d]".formatted(i, d));
                components[d] = component;
                sumOfSquares += component * component;
            }
            double norm = Math.sqrt(sumOfSquares);
            if (NumericGuards.isOutOfTolerance(norm, 1.0, UNIT_NORM_TOLERANCE)) {
                throw new IllegalArgumentException(
                        "l2NormalizedVectors[%d] must be L2-normalized to unit length (within %.0e), norm was %.15f"
                                .formatted(i, UNIT_NORM_TOLERANCE, norm));
            }
            vectors[i] = components;
        }
        int fixedDimension = dimension;

        List<Integer> labels = assignment.labels();
        List<List<Integer>> membersByLabel = new ArrayList<>(k);
        for (int c = 0; c < k; c++) {
            membersByLabel.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            membersByLabel.get(labels.get(i)).add(i);
        }

        double[][] centroids = new double[k][fixedDimension];
        double[] sigma = new double[k];
        for (int c = 0; c < k; c++) {
            List<Integer> members = membersByLabel.get(c);
            double[] centroid = centroids[c];
            for (int member : members) {
                double[] vector = vectors[member];
                for (int d = 0; d < fixedDimension; d++) {
                    centroid[d] += vector[d];
                }
            }
            for (int d = 0; d < fixedDimension; d++) {
                centroid[d] /= members.size();
            }
            double sigmaSum = 0.0;
            for (int member : members) {
                sigmaSum += euclideanDistance(vectors[member], centroid);
            }
            sigma[c] = sigmaSum / members.size();
        }

        double sum = 0.0;
        for (int i = 0; i < k; i++) {
            double max = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < k; j++) {
                if (j == i) {
                    continue;
                }
                double mIj = euclideanDistance(centroids[i], centroids[j]);
                // TRD §6.5: "Si M_ij = 0 ... el cociente se toma como +infinito", regardless
                // of the numerator — an exact equality against 0.0, never a tolerance (see
                // class Javadoc).
                double term = mIj == 0.0 ? Double.POSITIVE_INFINITY : (sigma[i] + sigma[j]) / mIj;
                if (term > max) {
                    max = term;
                }
            }
            sum += max;
        }

        double db = sum / k;
        if (Double.isInfinite(db)) {
            return OptionalDouble.empty();
        }
        // Defense in depth, matching this domain's "no derived double escapes unchecked"
        // convention: every term above is either a finite non-negative quotient or exactly
        // +Infinity, and infinities are already filtered above, so this can never actually
        // fire — but the house rule is to check anyway rather than trust the arithmetic.
        NumericGuards.requireFinite(db, "daviesBouldin");
        return OptionalDouble.of(db);
    }

    private static double euclideanDistance(double[] a, double[] b) {
        double sumOfSquares = 0.0;
        for (int d = 0; d < a.length; d++) {
            double diff = a[d] - b[d];
            sumOfSquares += diff * diff;
        }
        return Math.sqrt(sumOfSquares);
    }
}
