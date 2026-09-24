package co.edu.uniquindio.legajo.clustering;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The agglomerative merge loop shared by all four linkage criteria — the engine alone owns
 * the merge loop: repeatedly find the pair of currently-active clusters
 * with the smallest distance, merge it, and use {@code criterion}'s Lance-Williams
 * coefficients to recompute the merged cluster's distance to every other active cluster.
 *
 * <p><b>Cluster ids.</b> Original observations are {@code 0..n-1}; the cluster
 * created by merge {@code i} (0-based) is id {@code n+i}. A single {@code n+i x n+i}
 * working distance table (sized {@code 2n-1}) is filled lazily as merges create new ids —
 * an entry for an id that has not merged yet, or that has already been retired, is simply
 * never read.
 *
 * <p><b>Tie-breaking (determinism requirement).</b> Candidate pairs are scanned
 * in ascending {@code (a, b)} order with {@code a < b}, and a new candidate only replaces
 * the current best on a strictly smaller distance. Because the scan order already visits
 * pairs in lexicographic order, keeping the first-found minimum on ties is exactly "merge
 * the lexicographically smallest (idx1, idx2) pair first" — no separate tie-break step is
 * needed.
 *
 * <p><b>Complexity.</b> Naive O(n^3) time / O(n^2) space: each of the n-1 merges
 * scans all O(n^2) active pairs to find the minimum, then does O(n) work to update
 * distances to the remaining active clusters. SLINK/CLINK are explicit non-goals at the
 * n=20 reference corpus scale.
 *
 * <p><b>Only ever receives a validated {@link DistanceMatrix}</b>: this class
 * declares no method that accepts a raw {@code double[][]} in its place, so the only way to
 * reach {@code agglomerate} is through {@link DistanceMatrix#cosineDistance(List)} or
 * {@link DistanceMatrix#wardBase()} — both of which always derive from the run's selected
 * representation, never an arbitrary matrix.
 */
public final class LanceWilliamsEngine {

    /**
     * Runs the full agglomeration over {@code distanceMatrix} using {@code criterion},
     * returning the resulting {@link LinkageMatrix} of {@code n-1} rows (or zero rows for
     * the degenerate {@code n=1} case, where there is nothing to merge).
     */
    public LinkageMatrix agglomerate(DistanceMatrix distanceMatrix, LinkageCriterion criterion) {
        Objects.requireNonNull(distanceMatrix, "distanceMatrix");
        Objects.requireNonNull(criterion, "criterion");

        int n = distanceMatrix.size();
        int totalNodes = Math.max(1, 2 * n - 1);

        double[][] distance = new double[totalNodes][totalNodes];
        for (double[] row : distance) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        double[][] source = distanceMatrix.values();
        for (int i = 0; i < n; i++) {
            System.arraycopy(source[i], 0, distance[i], 0, n);
        }

        int[] size = new int[totalNodes];
        boolean[] active = new boolean[totalNodes];
        for (int i = 0; i < n; i++) {
            size[i] = 1;
            active[i] = true;
        }

        List<LinkageStep> rows = new ArrayList<>();
        int nextId = n;
        for (int merge = 0; merge < n - 1; merge++) {
            int bestA = -1;
            int bestB = -1;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int a = 0; a < nextId; a++) {
                if (!active[a]) {
                    continue;
                }
                for (int b = a + 1; b < nextId; b++) {
                    if (!active[b]) {
                        continue;
                    }
                    if (distance[a][b] < bestDistance) {
                        bestDistance = distance[a][b];
                        bestA = a;
                        bestB = b;
                    }
                }
            }

            int sizeA = size[bestA];
            int sizeB = size[bestB];
            int newSize = sizeA + sizeB;
            int newId = nextId++;

            for (int k = 0; k < newId; k++) {
                if (!active[k] || k == bestA || k == bestB) {
                    continue;
                }
                LanceWilliamsCoefficients coefficients = criterion.coefficients(sizeA, sizeB, size[k]);
                double updated = coefficients.alphaI() * distance[bestA][k]
                        + coefficients.alphaJ() * distance[bestB][k]
                        + coefficients.beta() * bestDistance
                        + coefficients.gamma() * Math.abs(distance[bestA][k] - distance[bestB][k]);
                distance[newId][k] = updated;
                distance[k][newId] = updated;
            }

            active[bestA] = false;
            active[bestB] = false;
            active[newId] = true;
            size[newId] = newSize;

            rows.add(new LinkageStep(bestA, bestB, bestDistance, newSize));
        }

        return new LinkageMatrix(rows);
    }
}
