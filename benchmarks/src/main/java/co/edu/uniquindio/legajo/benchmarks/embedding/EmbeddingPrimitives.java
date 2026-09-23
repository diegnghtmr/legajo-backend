package co.edu.uniquindio.legajo.benchmarks.embedding;

import java.util.List;

/**
 * The two O(d) single-pass loops {@code EmbeddingPrimitiveBenchmark} measures, extracted here
 * so a unit test can reach and prove them (odd/tasks/debt-cleanup.md, task B4). A plain
 * {@code @link} to that class is not used above: it lives in {@code benchmarks}' separate
 * {@code jmh} source set, which the {@code main} source set this class belongs to does not
 * depend on, so javadoc run against {@code main} alone cannot resolve it.
 *
 * <p>{@code co.edu.uniquindio.legajo.similarity.EmbeddingLocal#dotProduct} and {@code
 * co.edu.uniquindio.legajo.similarity.EmbeddingApi#sumSquaredDifferences} — the domain methods
 * this class mirrors — have package-private (default) access, scoped to the {@code
 * co.edu.uniquindio.legajo.similarity} package inside the {@code domain} module. That is exactly
 * why {@code co.edu.uniquindio.legajo.benchmarks.embedding} — a different package in a different
 * Gradle module — cannot call them directly: default access only reaches code in the same
 * package, and neither {@code benchmarks} nor its test package is that package. Living in
 * {@code benchmarks}' {@code main} source set (rather than inline inside the JMH class itself,
 * which lives in the separate {@code jmh} source set the {@code test} source set does not depend
 * on) is what lets a plain unit test reach the exact same loop code the JMH benchmark measures,
 * and then compare its result against the domain algorithms' public {@code compute(...)}.
 * Widening {@code EmbeddingLocal}/{@code EmbeddingApi}'s methods to {@code public} was rejected:
 * {@code benchmarks} must only ever depend on {@code domain}'s public surface (module map,
 * backend/AGENTS.md), so widening those two methods just to reach them from a different module
 * would leak internal algorithm detail across that boundary — this class keeps the boundary
 * intact while still proving the two hand-written loops compute the values the domain algorithms
 * actually rely on.
 */
public final class EmbeddingPrimitives {

    private EmbeddingPrimitives() {
    }

    /** Mirrors {@code EmbeddingLocal}'s cosine-by-dot-product primitive. */
    public static double dotProduct(List<Double> u, List<Double> v) {
        double sum = 0.0;
        for (int i = 0; i < u.size(); i++) {
            sum += u.get(i) * v.get(i);
        }
        return sum;
    }

    /** Mirrors {@code EmbeddingApi}'s Euclidean-distance-by-sum-of-squared-differences primitive. */
    public static double sumOfSquaredDifferences(List<Double> u, List<Double> v) {
        double sum = 0.0;
        for (int i = 0; i < u.size(); i++) {
            double diff = u.get(i) - v.get(i);
            sum += diff * diff;
        }
        return sum;
    }
}
