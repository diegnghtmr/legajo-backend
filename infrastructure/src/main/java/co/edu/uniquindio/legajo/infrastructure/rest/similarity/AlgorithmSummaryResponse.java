package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.application.similarity.AlgorithmSummary;
import co.edu.uniquindio.legajo.similarity.AlgorithmKind;

import java.util.Objects;

/** Wire shape of one row of {@code GET /api/v1/similarity/algorithms} (TRD §6.6). */
public record AlgorithmSummaryResponse(String id, String displayName, AlgorithmKind kind) {

    public AlgorithmSummaryResponse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(kind, "kind");
    }

    public static AlgorithmSummaryResponse from(AlgorithmSummary summary) {
        Objects.requireNonNull(summary, "summary");
        return new AlgorithmSummaryResponse(summary.id(), summary.displayName(), summary.kind());
    }
}
