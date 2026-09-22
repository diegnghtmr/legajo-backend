package co.edu.uniquindio.legajo.benchmarks.input;

import co.edu.uniquindio.legajo.corpus.Corpus;

import java.util.List;

/**
 * The real corpus's preprocessed tokens, flattened into one pool, computed once per JVM
 * (each JMH benchmark class runs with {@code @Fork(1)}, so this holder's static
 * initialization happens exactly once per run) and shared by every pairwise-curve benchmark
 * ({@code co.edu.uniquindio.legajo.benchmarks.pairwise}) that builds its synthetic token
 * sequences from it. Composition of {@link BenchmarkCorpus}'s already-tested steps; carries
 * no independent logic of its own.
 */
public final class BenchmarkTokenPool {

    private static final List<String> POOL = computePool();

    private BenchmarkTokenPool() {
    }

    /** The whole corpus's preprocessed tokens, concatenated in document order. */
    public static List<String> get() {
        return POOL;
    }

    private static List<String> computePool() {
        Corpus corpus = BenchmarkCorpus.load();
        List<List<String>> tokenStreams = BenchmarkCorpus.preprocessedTokenStreams(corpus);
        return BenchmarkCorpus.flattenTokenPool(tokenStreams);
    }
}
