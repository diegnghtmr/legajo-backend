package co.edu.uniquindio.legajo.benchmarks.input;

/**
 * Fixed seeds for this harness's deterministic synthetic inputs, following the fixed
 * performance-test protocol. One arbitrary constant per input family, chosen once
 * and never varied, so every run of this harness — on any machine — measures against the
 * exact same synthetic data, and only the wall-clock time differs.
 */
public final class BenchmarkSeeds {

    /** {@link SyntheticTokenSequences} inputs for the pairwise classic-algorithm curves. */
    public static final long PAIRWISE_TOKENS = 20260922L;

    /** {@link SyntheticUnitVectors}/{@link SyntheticDistanceMatrices} inputs for the HAC curves. */
    public static final long HAC_VECTORS = 20260922L;

    /** {@link SyntheticUnitVectors} inputs for the embedding-primitive benchmark. */
    public static final long EMBEDDING_VECTORS = 20260922L;

    private BenchmarkSeeds() {
    }
}
