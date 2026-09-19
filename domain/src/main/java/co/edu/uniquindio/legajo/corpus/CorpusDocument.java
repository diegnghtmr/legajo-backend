package co.edu.uniquindio.legajo.corpus;

import java.util.List;
import java.util.Objects;

/**
 * One document of the reference corpus (TRD §9 schema). Field {@code abstractText}
 * maps to the JSON field {@code abstract} — a reserved Java keyword — which the
 * infrastructure JSON adapter (task T4) is responsible for naming on the wire.
 *
 * <p>This record intentionally validates only structural invariants (no null field,
 * an immutable {@code authors} list) and does not enforce business rules such as
 * "non-blank title" or "abstractSha256 matches the recomputed hash": a corpus loaded
 * from disk may violate those rules, and {@link CorpusVerifier} needs to be able to
 * hold such a document in memory in order to report every violation (TRD §6.1, item
 * 6) rather than fail fast at construction time.
 */
public record CorpusDocument(
        String id,
        String title,
        List<String> authors,
        String abstractText,
        String source,
        String extractedBy,
        boolean manuallyValidated,
        String abstractSha256) {

    public CorpusDocument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(authors, "authors");
        authors = List.copyOf(authors);
        Objects.requireNonNull(abstractText, "abstractText");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(extractedBy, "extractedBy");
        Objects.requireNonNull(abstractSha256, "abstractSha256");
    }
}
