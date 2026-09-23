package co.edu.uniquindio.legajo.benchmarks.pairwise;

import co.edu.uniquindio.legajo.benchmarks.input.BenchmarkSeeds;
import co.edu.uniquindio.legajo.benchmarks.input.BenchmarkTokenPool;
import co.edu.uniquindio.legajo.benchmarks.input.SyntheticTokenSequences;
import co.edu.uniquindio.legajo.benchmarks.input.TokenSequencePair;
import co.edu.uniquindio.legajo.similarity.SimilarityContext;
import co.edu.uniquindio.legajo.similarity.SimilarityInput;
import co.edu.uniquindio.legajo.similarity.SimilarityResult;
import co.edu.uniquindio.legajo.similarity.TfIdfCorpusIndex;
import co.edu.uniquindio.legajo.similarity.TfIdfCosine;
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
 * TF-IDF cosine complexity curve (TRD §6.3: O(|A|+|B|) per pair once the corpus index is
 * built, which this benchmark's {@code @Setup} does once per trial and outside the measured
 * operation — the O(N·L) one-time corpus indexing TRD §6.3 also documents is deliberately
 * not part of what {@code pairwiseCompute()} measures, since the real service builds that
 * index once per run, never once per pair).
 *
 * <p><b>Author decision (TRD leaves this open, odd/tasks/jmh-benchmarks.md).</b>
 * {@code tfidf-cosine} needs a {@link SimilarityContext#tfIdfIndex()} for {@code df}/{@code
 * N}: this benchmark builds it from the two synthetic sequences under comparison themselves
 * (a 2-document corpus, {@code N = 2}), not from the real corpus. This keeps the benchmark
 * self-contained and its "corpus" scaling with the same {@code length} the curve varies,
 * rather than mixing a synthetic pair with a fixed real 20-document index; it changes the
 * constant term of the per-pair cost (a 2-document {@code df}/{@code idf} lookup, still
 * O(1) per term) but not the asymptotic shape being measured, which is O(|A|+|B|) either way.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class TfIdfCosineBenchmark {

    @Param({"50", "100", "200", "400", "800"})
    public int length;

    private final TfIdfCosine algorithm = new TfIdfCosine();
    private SimilarityInput inputA;
    private SimilarityInput inputB;
    private SimilarityContext context;

    @Setup(Level.Trial)
    public void setUp() {
        List<String> pool = BenchmarkTokenPool.get();
        TokenSequencePair pair = SyntheticTokenSequences.build(pool, length, BenchmarkSeeds.PAIRWISE_TOKENS);
        List<String> tokensA = pair.sequenceA();
        List<String> tokensB = pair.sequenceB();
        inputA = new SimilarityInput("", tokensA);
        inputB = new SimilarityInput("", tokensB);
        context = SimilarityContext.withTfIdfIndex(TfIdfCorpusIndex.from(List.of(tokensA, tokensB)));
    }

    @Benchmark
    public SimilarityResult pairwiseCompute() {
        return algorithm.compute(inputA, inputB, context);
    }
}
