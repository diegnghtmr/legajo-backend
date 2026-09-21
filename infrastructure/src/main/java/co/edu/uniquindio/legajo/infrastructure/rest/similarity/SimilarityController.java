package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.application.error.InvalidRequestException;
import co.edu.uniquindio.legajo.application.error.ResourceNotFoundException;
import co.edu.uniquindio.legajo.application.error.ProblemType;
import co.edu.uniquindio.legajo.application.error.UnknownIdentifierException;
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
 * lives in {@link SimilarityService} (application); this class shapes requests/responses and
 * classifies status by request location (TRD §6.6, task A7): a document/algorithm id or a
 * matrix selection that fails validation is always {@link InvalidRequestException} (400)
 * here, since every one of those values arrives in a body or query string. The one exception
 * is {@code trace}'s path-segment {@code algorithmId}: {@link SimilarityService} cannot know
 * that its caller is a path variable (the same lookup is reused by {@code compare}/{@code
 * matrix}'s body ids), so it raises the location-agnostic {@link UnknownIdentifierException}
 * and this class reclassifies it to {@link ResourceNotFoundException} (404) only in {@code
 * trace}. See {@link UnknownIdentifierException}'s Javadoc for the full reasoning.
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
        List<AlgorithmSimilarity> results;
        try {
            results = similarityService.compare(request.documentIdA(), request.documentIdB(), algorithmIds);
        } catch (UnknownIdentifierException exception) {
            // Every id compare() looks up (algorithmIds) is a request-body field here, so an
            // unknown identifier of any kind is 400, never 404 (TRD §6.6, task A7).
            throw asInvalidRequest(exception);
        }
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
            throw new InvalidRequestException(ProblemType.INVALID_SELECTION, "documentIds must not be empty");
        }

        List<List<CachedSimilarityResult>> rows;
        try {
            rows = similarityService.matrix(documentIds, request.algorithmId());
        } catch (UnknownIdentifierException exception) {
            // matrix()'s algorithmId and documentIds are both request-body fields here, so an
            // unknown identifier of any kind is 400, never 404 (TRD §6.6, task A7).
            throw asInvalidRequest(exception);
        }
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
        AlgorithmTrace trace;
        try {
            // documentIdA/documentIdB failures reach here already classified as
            // InvalidRequestException (400, requireDocument's calls are never path-based) and
            // pass through unchanged; only algorithmId — this endpoint's path segment — needs
            // reclassifying from the location-agnostic exception to a 404 (TRD §6.6, task A7).
            trace = similarityService.trace(algorithmId, documentIdA, documentIdB)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "no trace available for algorithm id: " + algorithmId));
        } catch (UnknownIdentifierException exception) {
            throw new ResourceNotFoundException(exception.type(), exception.getMessage());
        }
        return AlgorithmTraceMapper.toResponse(trace);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(fieldName + " must not be blank");
        }
    }

    /** compare()/matrix(): every id they look up is a request-body field, so any {@link
     * UnknownIdentifierException} they raise is always 400, whatever its {@link
     * co.edu.uniquindio.legajo.application.error.ProblemType} (TRD §6.6, task A7). */
    private static InvalidRequestException asInvalidRequest(UnknownIdentifierException exception) {
        return new InvalidRequestException(exception.type(), exception.getMessage());
    }
}
