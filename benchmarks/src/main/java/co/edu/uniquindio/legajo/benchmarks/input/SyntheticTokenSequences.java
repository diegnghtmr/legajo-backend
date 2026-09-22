package co.edu.uniquindio.legajo.benchmarks.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the two synthetic token sequences one pairwise classic-algorithm benchmark data
 * point needs (TRD §6.3's "Protocolo de pruebas de rendimiento (fijado)": "secuencias
 * sintéticas de tokens de longitud L ∈ {50, 100, 200, 400, 800} construidas concatenando
 * tokens del corpus").
 *
 * <p><b>How the two sequences are built.</b> {@code tokenPool} is the whole corpus's
 * preprocessed tokens concatenated in document order (the caller's responsibility, see
 * {@link BenchmarkCorpus}). Each sequence cycles that pool starting from a deterministic
 * offset derived from {@code seed}, wrapping around when {@code length} exceeds the pool
 * size — literally "concatenating/cycling" the corpus's own tokens, never inventing new
 * ones. {@code seed} picks two different starting offsets (never the same one) so the two
 * sequences of a pair are not simply identical copies of each other, while staying fully
 * reproducible: the same {@code (tokenPool, length, seed)} triple always yields the same
 * pair, run after run, machine after machine.
 */
public final class SyntheticTokenSequences {

    private SyntheticTokenSequences() {
    }

    /**
     * Builds one pair of length-{@code length} token sequences from {@code tokenPool}.
     * {@code seed} is any {@code long}; only its residue modulo the pool size matters, so
     * every seed value is valid.
     */
    public static TokenSequencePair build(List<String> tokenPool, int length, long seed) {
        Objects.requireNonNull(tokenPool, "tokenPool");
        if (tokenPool.isEmpty()) {
            throw new IllegalArgumentException("tokenPool must not be empty");
        }
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative, was " + length);
        }

        int poolSize = tokenPool.size();
        int offsetA = Math.floorMod(seed, poolSize);
        // A second, deliberately different offset (never equal to offsetA when poolSize > 1):
        // shifting by half the pool plus a fixed odd stride avoids the two sequences starting
        // at the same place, without needing randomness.
        int offsetB = Math.floorMod(offsetA + poolSize / 2 + 1, poolSize);

        return new TokenSequencePair(cycle(tokenPool, offsetA, length), cycle(tokenPool, offsetB, length));
    }

    private static List<String> cycle(List<String> pool, int start, int length) {
        int poolSize = pool.size();
        List<String> result = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            result.add(pool.get((start + i) % poolSize));
        }
        return result;
    }
}
