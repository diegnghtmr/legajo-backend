package co.edu.uniquindio.legajo.application.similarity;

import co.edu.uniquindio.legajo.similarity.AlgorithmKind;

import java.util.Objects;

/** {@code GET /similarity/algorithms} (TRD §6.6): the catalogue of the six fixed capabilities. */
public record AlgorithmSummary(String id, String displayName, AlgorithmKind kind) {

    public AlgorithmSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(kind, "kind");
    }
}
