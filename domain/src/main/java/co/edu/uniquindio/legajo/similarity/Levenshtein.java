package co.edu.uniquindio.legajo.similarity;

import java.util.Optional;

/**
 * Token-level Levenshtein edit distance (TRD §6.3, FTR-SIM). S1 lands only the identity
 * contract ({@code id}/{@code displayName}/{@code kind}) so {@link SimilarityAlgorithm}
 * has a real permitted type to be sealed against; {@code compute} and {@code trace} are
 * test-driven in S2, which replaces the placeholder bodies below with the hand-written DP
 * implementation (full matrix kept for the trace, fixed backtrace tie order diagonal →
 * up → left).
 */
public final class Levenshtein implements SimilarityAlgorithm {

    @Override
    public String id() {
        return "levenshtein";
    }

    @Override
    public String displayName() {
        return "Levenshtein";
    }

    @Override
    public AlgorithmKind kind() {
        return AlgorithmKind.CLASSIC;
    }

    @Override
    public SimilarityResult compute(SimilarityInput a, SimilarityInput b, SimilarityContext context) {
        throw new UnsupportedOperationException("Levenshtein.compute is implemented in S2");
    }

    @Override
    public Optional<AlgorithmTrace> trace(SimilarityInput a, SimilarityInput b, SimilarityContext context) {
        throw new UnsupportedOperationException("Levenshtein.trace is implemented in S2");
    }
}
