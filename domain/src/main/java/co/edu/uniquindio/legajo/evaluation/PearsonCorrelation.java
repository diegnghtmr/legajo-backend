package co.edu.uniquindio.legajo.evaluation;

import co.edu.uniquindio.legajo.similarity.NumericGuards;

import java.util.Objects;

/**
 * Hand-written Pearson correlation (R-02: no statistics or linear-algebra library), the
 * general-purpose primitive {@link CopheneticCorrelation} builds on to compare a tree's
 * cophenetic distances against D (TRD §6.5).
 *
 * <p>Uses the sum-of-products form
 * {@code r = (n*Sxy - Sx*Sy) / sqrt((n*Sxx - Sx^2) * (n*Syy - Sy^2))}, algebraically
 * equivalent to the textbook covariance/standard-deviation definition but computed in one
 * pass over each array instead of two (no separate mean-then-deviation pass).
 */
public final class PearsonCorrelation {

    private static final double TOLERANCE = 1e-9;

    private PearsonCorrelation() {
    }

    /**
     * Pearson's r between {@code x} and {@code y}, taken pairwise (index-aligned). Requires
     * at least two values and non-zero variance in both arrays — a zero-variance array would
     * otherwise divide the denominator to {@code 0.0} and silently return {@code NaN} or
     * {@code Infinity} instead of failing closed, the same shape of bug
     * {@link NumericGuards} exists to guard against elsewhere in this domain.
     */
    public static double of(double[] x, double[] y) {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        if (x.length != y.length) {
            throw new IllegalArgumentException(
                    "x and y must have the same length, was x=%d, y=%d".formatted(x.length, y.length));
        }
        int n = x.length;
        if (n < 2) {
            throw new IllegalArgumentException("at least two values are required, was " + n);
        }

        double sumX = 0.0;
        double sumY = 0.0;
        double sumXY = 0.0;
        double sumX2 = 0.0;
        double sumY2 = 0.0;
        for (int i = 0; i < n; i++) {
            double xi = x[i];
            double yi = y[i];
            NumericGuards.requireFinite(xi, "x[%d]".formatted(i));
            NumericGuards.requireFinite(yi, "y[%d]".formatted(i));
            sumX += xi;
            sumY += yi;
            sumXY += xi * yi;
            sumX2 += xi * xi;
            sumY2 += yi * yi;
        }

        double numerator = n * sumXY - sumX * sumY;
        double varianceTermX = n * sumX2 - sumX * sumX;
        double varianceTermY = n * sumY2 - sumY * sumY;
        double denominator = Math.sqrt(varianceTermX * varianceTermY);
        if (denominator <= TOLERANCE) {
            throw new IllegalArgumentException(
                    "Pearson correlation is undefined when x or y has zero variance");
        }

        return numerator / denominator;
    }
}
