package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Request body of {@code POST /api/v1/similarity/compare} (TRD §6.6). {@code algorithmIds}
 * is optional: {@code null} or an absent field defaults to all six capabilities (TAC-01,
 * PRD HU-1.1), the same default {@code SimilarityService.compare} already implements for an
 * empty list — the controller normalizes a {@code null} field to {@code List.of()} before
 * calling it, so this DTO's own default stays a thin, honest mirror of the wire body rather
 * than duplicating that business default.
 */
public record CompareRequest(
        @Nullable String documentIdA, @Nullable String documentIdB, @Nullable List<String> algorithmIds) {
}
