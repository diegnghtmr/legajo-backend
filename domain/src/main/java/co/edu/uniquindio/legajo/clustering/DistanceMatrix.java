package co.edu.uniquindio.legajo.clustering;

import co.edu.uniquindio.legajo.similarity.NumericGuards;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The validated n×n distance matrix RF2's four linkage criteria consume (TRD §6.4): every
 * entry finite and non-negative, symmetric within {@code TOLERANCE}, and a zero diagonal
 * within the same tolerance (a document's distance to itself is exactly 0 by definition).
 *
 * <p><b>D = 1 − cos(V) (TRD §6.4).</b> {@link #cosineDistance(List)} is the only public way
 * to build one: it takes the L2-normalized vectors of the run's selected representation and
 * derives D by hand (R-02), one cosine per pair, clamped to {@code [-1, 1]} before the
 * subtraction so floating-point drift at the boundary (e.g. a self-cosine of
 * {@code 1.0000000001}) can never produce a negative distance that would fail this very
 * class's own non-negativity check. Every input vector's L2 norm is checked to be within
 * tolerance of 1: the precondition ("the representation's L2-normalized vectors") is
 * enforced here, not merely trusted from the caller.
 *
 * <p><b>Ward's base, D_w = 2·D (TRD §6.4).</b> {@link #wardBase()} doubles an existing,
 * already-validated {@code DistanceMatrix} — it never accepts a second raw array. This is
 * deliberate: TRD §13's mandatory Ward test requires "el constructor acepta únicamente D
 * derivada de la representación seleccionada (sin entrada de matriz arbitraria)". The
 * canonical constructor below is package-private, not public, so no code outside {@code
 * clustering} can hand this type an arbitrary {@code double[][]} and label it a distance
 * matrix: {@link #cosineDistance(List)} (which always derives D from representation
 * vectors) and {@link #wardBase()} (which only ever doubles an existing D) are the sole
 * public entry points. The package-private constructor stays reachable from this class's
 * own validation tests and from the future {@code LanceWilliamsEngine}/linkage classes in
 * this same package (R2/R3) — TRD §13's constraint is really aimed at whichever class ends
 * up modeling the Ward criterion itself, and that class is expected to keep taking a
 * {@code DistanceMatrix} (produced only via {@link #wardBase()}), never a raw array, for
 * exactly this reason.
 *
 * <p>Deep-copies {@code values} on construction and on every {@link #values()} read, and
 * overrides {@code equals}/{@code hashCode} with {@link Arrays#deepEquals}/{@link
 * Arrays#deepHashCode} (the {@code DpMatrixTrace} template from {@code similarity}), because
 * a {@code double[][]} field is otherwise a mutable hole in an immutable value type.
 */
public final class DistanceMatrix {

    private static final double TOLERANCE = 1e-9;

    private final double[][] values;

    /**
     * Package-private: see the class Javadoc for why this is not part of the public API.
     * Validates squareness, then every entry's finiteness/non-negativity, then the zero
     * diagonal, then symmetry, before defensively copying.
     */
    DistanceMatrix(double[][] values) {
        Objects.requireNonNull(values, "values");
        if (values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        int n = values.length;
        for (double[] row : values) {
            Objects.requireNonNull(row, "values must not contain a null row");
            if (row.length != n) {
                throw new IllegalArgumentException(
                        "values must be square (%d x %d), found a row of length %d".formatted(n, n, row.length));
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                NumericGuards.requireNonNegativeFinite(values[i][j], "values[%d][%d]".formatted(i, j));
            }
        }
        for (int i = 0; i < n; i++) {
            if (NumericGuards.isOutOfTolerance(values[i][i], 0.0, TOLERANCE)) {
                throw new IllegalArgumentException(
                        "values[%d][%d] must be 0 (zero diagonal) within %.0e, was %.15f"
                                .formatted(i, i, TOLERANCE, values[i][i]));
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (NumericGuards.isOutOfTolerance(values[i][j], values[j][i], TOLERANCE)) {
                    throw new IllegalArgumentException(
                            "values must be symmetric within %.0e: values[%d][%d]=%.15f, values[%d][%d]=%.15f"
                                    .formatted(TOLERANCE, i, j, values[i][j], j, i, values[j][i]));
                }
            }
        }
        this.values = deepCopy(values);
    }

    /**
     * Builds D = 1 − cos(V) (TRD §6.4) by hand from {@code l2NormalizedVectors}, one entry
     * per document in a fixed order; row/column {@code i} is document {@code i}. Every
     * vector must already be L2-normalized to unit length (within {@code TOLERANCE}) and
     * share the same dimension — the precondition {@code EmbeddingVector} and the
     * corpus-wide TF-IDF materializer both enforce at their own construction, checked again
     * here so this, the only public entry point, never silently derives D from an
     * un-normalized space.
     */
    public static DistanceMatrix cosineDistance(List<List<Double>> l2NormalizedVectors) {
        Objects.requireNonNull(l2NormalizedVectors, "l2NormalizedVectors");
        if (l2NormalizedVectors.isEmpty()) {
            throw new IllegalArgumentException("l2NormalizedVectors must not be empty");
        }
        int n = l2NormalizedVectors.size();
        int dimension = -1;
        double[][] unit = new double[n][];
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
            if (NumericGuards.isOutOfTolerance(norm, 1.0, TOLERANCE)) {
                throw new IllegalArgumentException(
                        "l2NormalizedVectors[%d] must be L2-normalized to unit length (within %.0e), norm was %.15f"
                                .formatted(i, TOLERANCE, norm));
            }
            unit[i] = components;
        }

        double[][] distances = new double[n][n];
        int fixedDimension = dimension;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    distances[i][j] = 0.0;
                    continue;
                }
                double dotProduct = 0.0;
                for (int d = 0; d < fixedDimension; d++) {
                    dotProduct += unit[i][d] * unit[j][d];
                }
                double clampedCosine = Math.max(-1.0, Math.min(1.0, dotProduct));
                distances[i][j] = 1.0 - clampedCosine;
            }
        }
        return new DistanceMatrix(distances);
    }

    /**
     * D_w = 2·D (TRD §6.4): Ward's base, derived only by doubling this already-validated D
     * — never accepted as a second raw matrix (see class Javadoc / TRD §13).
     */
    public DistanceMatrix wardBase() {
        int n = values.length;
        double[][] doubled = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                doubled[i][j] = 2.0 * values[i][j];
            }
        }
        return new DistanceMatrix(doubled);
    }

    /** The matrix dimension n. */
    public int size() {
        return values.length;
    }

    /** A defensive deep copy of the underlying n×n values. */
    public double[][] values() {
        return deepCopy(values);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DistanceMatrix that)) {
            return false;
        }
        return Arrays.deepEquals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(values);
    }

    private static double[][] deepCopy(double[][] source) {
        double[][] copy = new double[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = Arrays.copyOf(source[i], source[i].length);
        }
        return copy;
    }
}
