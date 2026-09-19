package co.edu.uniquindio.legajo.corpus;

/**
 * The closed set of rules {@link CorpusVerifier} checks (TRD §6.1, item 6, plus the
 * corpus-wide hash cross-check this verifier also performs).
 */
public enum CorpusRule {
    /** {@code |documents| = sourceCount}. */
    DOCUMENT_COUNT_MATCHES_SOURCE_COUNT,
    /** Optional cross-check against a caller-supplied expected reference count. */
    DOCUMENT_COUNT_MATCHES_EXPECTED,
    /** {@code n >= 3}. */
    MINIMUM_DOCUMENT_COUNT,
    /** No two documents share the same {@code abstractSha256}. */
    NO_DUPLICATE_ABSTRACT_SHA256,
    /**
     * No two documents share the same {@code id}. A duplicate id makes {@code
     * corpusSha256} order-ambiguous, because {@link CorpusHasher#corpusSha256} sorts
     * documents by id before concatenating them (advisory raised in T3's review,
     * checked here rather than left as a silent hash collision risk).
     */
    UNIQUE_DOCUMENT_ID,
    /** {@code title} is not empty. */
    NON_BLANK_TITLE,
    /** {@code authors} is not empty. */
    NON_EMPTY_AUTHORS,
    /** {@code abstract} is not empty. */
    NON_BLANK_ABSTRACT,
    /** The preprocessed token stream of {@code abstract} (TRD §6.2) is not empty. */
    NON_EMPTY_PREPROCESSED_TOKENS,
    /** {@code manuallyValidated = true}. */
    MANUALLY_VALIDATED,
    /** The recalculated sha256 of {@code abstract} matches the frozen {@code abstractSha256}. */
    ABSTRACT_SHA256_MATCHES_RECOMPUTED,
    /** The recalculated corpus hash matches the frozen {@code corpusSha256}. */
    CORPUS_SHA256_MATCHES_RECOMPUTED
}
