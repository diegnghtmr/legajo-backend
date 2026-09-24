package co.edu.uniquindio.legajo.infrastructure.rest.corpus;

import co.edu.uniquindio.legajo.application.corpus.CorpusSummary;

import java.util.List;
import java.util.Objects;

/**
 * Wire shape of one row of {@code GET /api/v1/corpus}: {@code id}, {@code title},
 * and {@code authors} only, never the abstract. A dedicated DTO — rather than serializing
 * {@link CorpusSummary} (application) directly — keeps the wire contract free to evolve
 * independently of the application layer's own shape, per this feature's placement decision
 * (controllers/DTOs live in {@code infrastructure}, mapping domain/application types at the
 * boundary).
 */
public record CorpusSummaryResponse(String id, String title, List<String> authors) {

    public CorpusSummaryResponse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(authors, "authors");
        authors = List.copyOf(authors);
    }

    public static CorpusSummaryResponse from(CorpusSummary summary) {
        Objects.requireNonNull(summary, "summary");
        return new CorpusSummaryResponse(summary.id(), summary.title(), summary.authors());
    }
}
