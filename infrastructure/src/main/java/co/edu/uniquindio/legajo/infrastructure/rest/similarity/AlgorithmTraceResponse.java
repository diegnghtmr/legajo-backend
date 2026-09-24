package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

/**
 * Marker for {@code GET /api/v1/similarity/{algorithmId}/trace}'s response body:
 * mirrors the domain's sealed {@code AlgorithmTrace} one-to-one, so
 * {@link AlgorithmTraceMapper} stays exhaustive without a {@code default} branch when the
 * domain adds a seventh capability.
 */
public sealed interface AlgorithmTraceResponse
        permits DpMatrixTraceResponse, JaccardTraceResponse, TfIdfCosineTraceResponse, EmbeddingLocalTraceResponse,
        EmbeddingApiTraceResponse {
}
