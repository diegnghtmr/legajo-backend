package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Request body of {@code POST /api/v1/similarity/matrix} (TRD §6.6): one algorithm over a
 * selection of {@code documentIds}, {@code m = documentIds.size()} required in {@code [3,
 * n]}. Range and duplicate validation both live in {@code SimilarityService.matrix}
 * (application), not here — this DTO only carries the two fields across the wire.
 */
public record MatrixRequest(@Nullable String algorithmId, @Nullable List<String> documentIds) {
}
