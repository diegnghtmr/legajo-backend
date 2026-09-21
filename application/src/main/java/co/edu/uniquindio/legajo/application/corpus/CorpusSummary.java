package co.edu.uniquindio.legajo.application.corpus;

import java.util.List;
import java.util.Objects;

/**
 * The listing shape {@code GET /corpus} needs (TRD §6.6): {@code id}, {@code title}, and
 * {@code authors} only — never the abstract, which {@code GET /corpus/{id}} alone exposes.
 */
public record CorpusSummary(String id, String title, List<String> authors) {

    public CorpusSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(authors, "authors");
        authors = List.copyOf(authors);
    }
}
