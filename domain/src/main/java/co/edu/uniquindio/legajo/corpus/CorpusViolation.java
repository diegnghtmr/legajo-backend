package co.edu.uniquindio.legajo.corpus;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One violation reported by {@link CorpusVerifier}. {@code documentId} is {@code null}
 * for a corpus-wide rule (e.g. total document count or the corpus hash) and set for a
 * per-document rule, which lets {@link CorpusVerificationResult} list every violation
 * without forcing an artificial document id onto corpus-level problems.
 */
public record CorpusViolation(CorpusRule rule, @Nullable String documentId, String message) {

    public CorpusViolation {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(message, "message");
    }

    public static CorpusViolation forDocument(CorpusRule rule, String documentId, String message) {
        Objects.requireNonNull(documentId, "documentId");
        return new CorpusViolation(rule, documentId, message);
    }

    public static CorpusViolation forCorpus(CorpusRule rule, String message) {
        return new CorpusViolation(rule, null, message);
    }

    public boolean isCorpusLevel() {
        return documentId == null;
    }
}
