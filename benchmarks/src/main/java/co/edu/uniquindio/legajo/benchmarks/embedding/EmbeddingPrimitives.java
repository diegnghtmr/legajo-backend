package co.edu.uniquindio.legajo.benchmarks.embedding;

import java.util.List;

/**
 * The two O(d) single-pass loops {@link co.edu.uniquindio.legajo.benchmarks.embedding
 * .EmbeddingPrimitiveBenchmark} measures, extracted here so a unit test can reach and prove
 * them (odd/tasks/debt-cleanup.md, task B4).
 *
 * <p>{@code co.edu.uniquindio.legajo.similarity.EmbeddingLocal#dotProduct} and {@code
 * co.edu.uniquindio.legajo.similarity.EmbeddingApi#sumSquaredDifferences} — the domain
 * methods this class mirrors — are package-private, so neither {@code
 * co.edu.uniquindio.legajo.benchmarks.embedding} nor its test package can call them directly.
 * Living in {@code benchmarks}' {@code main} source set (rather than inline inside the JMH
 * class itself, which lives in the separate {@code jmh} source set the {@code test} source set
 * does not depend on) is what lets a plain unit test reach the exact same loop code the JMH
 * benchmark measures, and then compare its result against the domain algorithms' public {@code
 * compute(...)}. Widening {@code EmbeddingLocal}/{@code EmbeddingApi}'s methods to
 * package-private-across-modules (or public) was rejected: those methods are deliberately
 * private implementation detail of two {@code domain.similarity} algorithms, and {@code
 * benchmarks} must only ever depend on {@code domain}'s public surface (module map,
 * backend/AGENTS.md) — this class keeps that boundary intact while still proving the two
 * hand-written loops compute the values the domain algorithms actually rely on.
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
