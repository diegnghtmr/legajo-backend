package co.edu.uniquindio.legajo.benchmarks.pairwise;

import co.edu.uniquindio.legajo.benchmarks.input.BenchmarkSeeds;
import co.edu.uniquindio.legajo.benchmarks.input.BenchmarkTokenPool;
import co.edu.uniquindio.legajo.benchmarks.input.SyntheticTokenSequences;
import co.edu.uniquindio.legajo.benchmarks.input.TokenSequencePair;
import co.edu.uniquindio.legajo.similarity.Jaccard;
import co.edu.uniquindio.legajo.similarity.SimilarityContext;
import co.edu.uniquindio.legajo.similarity.SimilarityInput;
import co.edu.uniquindio.legajo.similarity.SimilarityResult;
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
 * Jaccard complexity curve (TRD §6.3: O(|A|+|B|) time/space with hash sets), same
 * synthetic-input shape as {@link LevenshteinBenchmark}. No cache is involved.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class JaccardBenchmark {

    @Param({"50", "100", "200", "400", "800"})
    public int length;

    private final Jaccard algorithm = new Jaccard();
    private SimilarityInput inputA;
    private SimilarityInput inputB;

    @Setup(Level.Trial)
    public void setUp() {
        List<String> pool = BenchmarkTokenPool.get();
        TokenSequencePair pair = SyntheticTokenSequences.build(pool, length, BenchmarkSeeds.PAIRWISE_TOKENS);
        inputA = new SimilarityInput("", pair.sequenceA());
        inputB = new SimilarityInput("", pair.sequenceB());
    }

    @Benchmark
    public SimilarityResult pairwiseCompute() {
        return algorithm.compute(inputA, inputB, SimilarityContext.EMPTY);
    }
}
