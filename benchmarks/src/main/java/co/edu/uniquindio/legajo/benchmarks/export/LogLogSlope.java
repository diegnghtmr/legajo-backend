package co.edu.uniquindio.legajo.benchmarks.export;

import java.util.List;
import java.util.Objects;

/**
 * The least-squares slope of {@code ln(score)} against {@code ln(size)} over a set of
 * {@link SizeScore} points (TRD §6.3/§6.4's "las curvas se trazan frente al orden teórico y
 * se reporta la pendiente log-log", TAC-18): a curve whose score grows as {@code size^p}
 * yields a slope of exactly {@code p}, which is what makes this comparable directly against
 * this harness's documented theoretical exponents ({@link BenchmarkFamilies}).
 */
public final class LogLogSlope {

    private LogLogSlope() {
    }

    /**
     * Ordinary least-squares slope of {@code y = ln(score)} against {@code x = ln(size)}:
     * {@code slope = Σ((x - x̄)(y - ȳ)) / Σ((x - x̄)²)}. Requires at least two points (a slope
     * is undefined for one) and every size/score to be strictly positive (a logarithm of a
     * non-positive number is undefined).
     */
    public static double of(List<SizeScore> points) {
        Objects.requireNonNull(points, "points");
        if (points.size() < 2) {
            throw new IllegalArgumentException(
                    "at least 2 points are required to compute a slope, was " + points.size());
        }

        double[] x = new double[points.size()];
        double[] y = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            SizeScore point = points.get(i);
            if (point.size() <= 0.0) {
                throw new IllegalArgumentException("size must be positive, was " + point.size());
            }
            if (point.score() <= 0.0) {
                throw new IllegalArgumentException("score must be positive, was " + point.score());
            }
            x[i] = Math.log(point.size());
            y[i] = Math.log(point.score());
        }

        double meanX = mean(x);
        double meanY = mean(y);

        double numerator = 0.0;
        double denominator = 0.0;
        for (int i = 0; i < x.length; i++) {
            double dx = x[i] - meanX;
            numerator += dx * (y[i] - meanY);
            denominator += dx * dx;
        }

        if (denominator == 0.0) {
            throw new IllegalArgumentException("every point has the same size; a slope needs at least 2 distinct sizes");
        }
        return numerator / denominator;
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }
}
