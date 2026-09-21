package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.application.error.InvalidRequestException;
import co.edu.uniquindio.legajo.application.error.ResourceNotFoundException;
import co.edu.uniquindio.legajo.application.similarity.AlgorithmSimilarity;
import co.edu.uniquindio.legajo.application.similarity.CachedSimilarityResult;
import co.edu.uniquindio.legajo.application.similarity.SimilarityService;
import co.edu.uniquindio.legajo.similarity.AlgorithmTrace;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * The four similarity endpoints of TRD §6.6 (feature doc task A3): {@code
 * POST /similarity/compare}, {@code POST /similarity/matrix}, {@code
 * GET /similarity/{algorithmId}/trace}, and {@code GET /similarity/algorithms}. Pure
 * adapter: every rule (algorithm defaulting, matrix selection bounds, unknown-id lookups)
 * lives in {@link SimilarityService} (application); this class only shapes requests/
 * responses and lets {@link ResourceNotFoundException} (unknown algorithm/document id) and
 * {@link InvalidRequestException} (validation, task A3b) bubble up to {@code
 * ProblemDetailExceptionHandler}.
 *
 * <p><b>Trace pair identification — a TRD gap, resolved here.</b> TRD §6.6 fixes the path
 * {@code GET /similarity/{algorithmId}/trace} but never says how the two compared documents
 * are named on that request; unlike {@code compare} (a JSON body with two id fields) or
 * {@code matrix} (a body with a list), a {@code GET} conventionally carries no body. This
 * class resolves the gap with two required query parameters, {@code documentIdA} and
 * {@code documentIdB}, the least surprising shape for a two-document {@code GET} (mirrors
 * {@code compare}'s two body fields, just as query parameters) and the one A6's OpenAPI
 * document can describe unambiguously. Flagged here rather than silently invented; the
 * author may prefer path segments or a different parameter naming.
 */
@RestController
@RequestMapping("/api/v1/similarity")
public class SimilarityController {

    private final SimilarityService similarityService;

    public SimilarityController(SimilarityService similarityService) {
        this.similarityService = Objects.requireNonNull(similarityService, "similarityService");
    }

    /** {@code GET /api/v1/similarity/algorithms}: the catalogue, in registration order. */
    @GetMapping("/algorithms")
    public List<AlgorithmSummaryResponse> algorithms() {
        return similarityService.catalogue().stream().map(AlgorithmSummaryResponse::from).toList();
    }

    /**
     * {@code POST /api/v1/similarity/compare}: {@code algorithmIds} omitted or {@code null}
     * defaults to all six (TAC-01, TRD §6.6).
     */
    @PostMapping("/compare")
    public List<AlgorithmSimilarityResponse> compare(@RequestBody CompareRequest request) {
        requireNonBlank(request.documentIdA(), "documentIdA");
        requireNonBlank(request.documentIdB(), "documentIdB");

        List<String> algorithmIds = request.algorithmIds() == null ? List.of() : request.algorithmIds();
        List<AlgorithmSimilarity> results =
                similarityService.compare(request.documentIdA(), request.documentIdB(), algorithmIds);
        return results.stream().map(AlgorithmSimilarityResponse::from).toList();
    }

    /**
     * {@code POST /api/v1/similarity/matrix}: m×m over {@code documentIds} for one
     * algorithm. Selection-size and duplicate-id validation live in
     * {@code SimilarityService.matrix}.
     */
    @PostMapping("/matrix")
    public List<List<SimilarityResultResponse>> matrix(@RequestBody MatrixRequest request) {
        requireNonBlank(request.algorithmId(), "algorithmId");
        List<String> documentIds = request.documentIds();
        if (documentIds == null || documentIds.isEmpty()) {
            throw new InvalidRequestException("documentIds must not be empty");
        }

        List<List<CachedSimilarityResult>> rows = similarityService.matrix(documentIds, request.algorithmId());
        return rows.stream()
                .map(row -> row.stream().map(SimilarityResultResponse::from).toList())
                .toList();
    }

    /**
     * {@code GET /api/v1/similarity/{algorithmId}/trace}: the complete trace for one pair,
     * no truncation parameter (NFR-QA-03). See this class's Javadoc for the pair-
     * identification decision.
     */
    @GetMapping("/{algorithmId}/trace")
    public AlgorithmTraceResponse trace(@PathVariable("algorithmId") String algorithmId,
            @RequestParam("documentIdA") String documentIdA, @RequestParam("documentIdB") String documentIdB) {
        AlgorithmTrace trace = similarityService.trace(algorithmId, documentIdA, documentIdB)
                .orElseThrow(() -> new ResourceNotFoundException("no trace available for algorithm id: " + algorithmId));
        return AlgorithmTraceMapper.toResponse(trace);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(fieldName + " must not be blank");
        }
    }
}
