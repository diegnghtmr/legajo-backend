package co.edu.uniquindio.legajo.similarity;

import java.util.Optional;

/**
 * Common contract for the six similarity capabilities of TRD §6.3 (FTR-SIM), hand-written
 * under R-02: {@code id}/{@code displayName} for the registry and UI, {@code kind} to
 * group the four classical algorithms and {@code tfidf-cosine} apart from the two
 * embedding-based ones, {@code compute} for the numeric result, and {@code trace} for the
 * optional step-by-step evidence (empty when a capability has no trace to show).
 *
 * <p><b>Input type.</b> {@code a} and {@code b} are {@link SimilarityInput}, which carries
 * both text representations a capability might need: the preprocessed token stream (TRD
 * §6.2) for the classical capabilities and {@code tfidf-cosine}, and the raw cleaned
 * abstract for {@code embedding-local}/{@code embedding-api} (TRD §6.3, "Texto de entrada
 * por familia" — each pretrained embedding model owns its own tokenizer, delegable under
 * R-02). A single bundled input type keeps this interface's {@code compute}/{@code trace}
 * shape identical for every capability, so the sealed permits list below can host all six
 * without a second, kind-specific method pair or without callers branching on
 * {@link AlgorithmKind} before invoking a capability.
 *
 * <p><b>Extensibility (TRD §4.4).</b> {@link Levenshtein} (S2), {@link NeedlemanWunsch}
 * (S3), {@link Jaccard} (S4), {@link TfIdfCosine} (S5), {@link EmbeddingLocal} (S6a), and
 * {@link EmbeddingApi} (S6b) are the six permitted capabilities TRD §6.3 requires. The
 * {@code @Component} list injection that collects every permitted instance into a registry
 * lives in {@code infrastructure} — this package stays framework-free (ArchUnit-enforced).
 */
public sealed interface SimilarityAlgorithm
        permits Levenshtein, NeedlemanWunsch, Jaccard, TfIdfCosine, EmbeddingLocal, EmbeddingApi {

    /** Stable identifier used by the API, cache keys, and the registry (e.g. "levenshtein"). */
    String id();

    /** Human-readable label for the UI (e.g. "Levenshtein"). */
    String displayName();

    /** Which family (TRD §6.3) this capability belongs to. */
    AlgorithmKind kind();

    /**
     * Computes the similarity between {@code a} and {@code b}. {@code computedNanos} in
     * the returned {@link SimilarityResult} must be measured inside this method, so a
     * cache hit at the calling layer never reports lookup time as algorithm time
     * (TRD §6.3).
     */
    SimilarityResult compute(SimilarityInput a, SimilarityInput b, SimilarityContext context);

    /**
     * Returns the step-by-step evidence for this computation, when this capability
     * exposes one (TRD §6.3 lists a trace for every one of the six capabilities, so this
     * is expected to be non-empty for every permitted implementation).
     */
    Optional<AlgorithmTrace> trace(SimilarityInput a, SimilarityInput b, SimilarityContext context);
}
