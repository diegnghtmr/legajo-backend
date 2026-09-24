package co.edu.uniquindio.legajo.benchmarks.embedding;

import co.edu.uniquindio.legajo.benchmarks.input.BenchmarkSeeds;
import co.edu.uniquindio.legajo.benchmarks.input.SyntheticUnitVectors;
import co.edu.uniquindio.legajo.similarity.EmbeddingApi;
import co.edu.uniquindio.legajo.similarity.EmbeddingLocal;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The O(d) single-pass primitive underneath both embedding metrics: {@code embedding-local}'s
 * cosine dot product and {@code embedding-api}'s Euclidean sum of squared differences, each
 * measured once at d = 384 (MiniLM) and once at d = 1536 (Gemini) — a single timing
 * measurement per dimension, with no growth curve required since the cost is linear in d.
 *
 * <p><b>Author decision.</b> {@link EmbeddingLocal#dotProduct} and
 * {@link EmbeddingApi#sumSquaredDifferences} are package-private static methods in
 * {@code co.edu.uniquindio.legajo.similarity}, unreachable from this module's
 * {@code co.edu.uniquindio.legajo.benchmarks.embedding} package. This class instead delegates
 * to {@link EmbeddingPrimitives}, which hand-writes the exact same two single-pass loops those
 * methods run (normalization/trace overhead is intentionally out of scope), so what is
 * measured is the shared O(d) shape both embedding algorithms actually pay at compare time,
 * not an approximation of it — {@code EmbeddingPrimitivesCrossCheckTest} proves those loops
 * against the domain algorithms' own public {@code compute(...)}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class EmbeddingPrimitiveBenchmark {

    @Param({"384", "1536"})
    public int dimension;

    private List<Double> u;
    private List<Double> v;

    @Setup(Level.Trial)
    public void setUp() {
        List<List<Double>> vectors = SyntheticUnitVectors.generate(2, dimension, BenchmarkSeeds.EMBEDDING_VECTORS);
        u = vectors.get(0);
        v = vectors.get(1);
    }

    /** Mirrors {@link EmbeddingLocal}'s cosine-by-dot-product primitive. */
    @Benchmark
    public double dotProduct() {
        return EmbeddingPrimitives.dotProduct(u, v);
    }

    /** Mirrors {@link EmbeddingApi}'s Euclidean-distance-by-sum-of-squared-differences primitive. */
    @Benchmark
    public double euclideanSumOfSquaredDifferences() {
        return EmbeddingPrimitives.sumOfSquaredDifferences(u, v);
    }
}
