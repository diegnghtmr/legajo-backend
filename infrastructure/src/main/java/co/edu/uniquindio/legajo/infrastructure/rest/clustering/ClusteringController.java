package co.edu.uniquindio.legajo.infrastructure.rest.clustering;

import co.edu.uniquindio.legajo.application.clustering.ClusteringCutResult;
import co.edu.uniquindio.legajo.application.clustering.ClusteringService;
import co.edu.uniquindio.legajo.application.clustering.LinkageEvaluationOnly;
import co.edu.uniquindio.legajo.application.clustering.LinkageRunResult;
import co.edu.uniquindio.legajo.application.error.InvalidRequestException;
import co.edu.uniquindio.legajo.application.error.ProblemType;
import co.edu.uniquindio.legajo.similarity.Representation;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * The three clustering endpoints: {@code
 * POST /clustering}, {@code POST /clustering/evaluation}, {@code POST /clustering/cut}. Pure
 * adapter: every clustering rule (representation defaulting, linkage resolution, the
 * fixed-cut set, {@code cut}'s free-{@code k} range) lives in
 * {@link ClusteringService} (application); this class only parses the request DTOs, shapes
 * the responses, and lets {@link InvalidRequestException} (unknown linkage id, unknown
 * representation id, a missing or out-of-range {@code k} — all 400) bubble up to
 * {@code ProblemDetailExceptionHandler}.
 *
 * <p><b>Classifying an unknown {@code representation} id.</b> {@link Representation#fromId}
 * throws a raw {@link IllegalArgumentException} that would otherwise fall through to a 500
 * (a raw JDK exception is presumed a server bug). This class instead maps that failure to
 * {@link InvalidRequestException} (400, {@code urn:legajo:problem:unknown-representation}):
 * {@code representation} is not looked up in any registry or catalogue — it is a plain
 * computation-mode selector sent in the request body, and only path-identified resources are
 * classified 404.
 *
 * <p><b>{@code linkage} is 400 too, for the same reason.</b> An unknown
 * {@code linkage}/{@code linkages} id is 400, not 404, since {@code linkage}
 * is likewise never a path segment on any endpoint here — always a request-body field, so an
 * unknown value is a request-validation failure, not a missing resource. {@code
 * ClusteringService.resolveLinkage} now throws {@link InvalidRequestException} directly
 * ({@code urn:legajo:problem:unknown-linkage}); this class does not need to intervene because,
 * unlike a similarity algorithm id (which is 404 for {@code GET
 * /similarity/{algorithmId}/trace}'s path segment but 400 for a compare/matrix body id), a
 * linkage id has no path-based use anywhere to disambiguate from.
 */
@RestController
@RequestMapping("/api/v1/clustering")
public class ClusteringController {

    private final ClusteringService clusteringService;

    public ClusteringController(ClusteringService clusteringService) {
        this.clusteringService = Objects.requireNonNull(clusteringService, "clusteringService");
    }

    /**
     * {@code POST /api/v1/clustering}: {@code representation} defaults to
     * {@link Representation#DEFAULT}, {@code linkages} defaults to all four, and an omitted body
     * applies both defaults.
     */
    @PostMapping
    public List<LinkageResultResponse> run(@RequestBody(required = false) @Nullable ClusteringRequest body) {
        ClusteringRequest request = orDefaults(body);
        Representation representation = parseRepresentation(request.representation());
        List<String> linkageIds = linkageIds(request);

        List<LinkageRunResult> results = clusteringService.run(representation, linkageIds);
        return results.stream().map(LinkageResultResponse::from).toList();
    }

    /** {@code POST /api/v1/clustering/evaluation}: the same computation as {@link #run},
     * evaluation block only. */
    @PostMapping("/evaluation")
    public List<LinkageEvaluationResponse> evaluation(@RequestBody(required = false) @Nullable ClusteringRequest body) {
        ClusteringRequest request = orDefaults(body);
        Representation representation = parseRepresentation(request.representation());
        List<String> linkageIds = linkageIds(request);

        List<LinkageEvaluationOnly> results = clusteringService.evaluateOnly(representation, linkageIds);
        return results.stream().map(LinkageEvaluationResponse::from).toList();
    }

    /**
     * {@code POST /api/v1/clustering/cut}: the only endpoint accepting a free {@code k}.
     * {@code k}'s range check lives in {@link ClusteringService#cut} (the application
     * boundary); this method only guards against unboxing a {@code
     * null} {@code k} (which would otherwise throw an unclassified
     * {@link NullPointerException} — a bug, not a validation failure).
     */
    @PostMapping("/cut")
    public ClusterAssignmentResponse cut(@RequestBody ClusteringCutRequest request) {
        Representation representation = parseRepresentation(request.representation());
        if (request.linkage() == null || request.linkage().isBlank()) {
            throw new InvalidRequestException("linkage must not be blank");
        }
        if (request.k() == null) {
            throw new InvalidRequestException("k must not be null");
        }

        ClusteringCutResult result = clusteringService.cut(representation, request.linkage(), request.k());
        return ClusterAssignmentResponse.from(result);
    }

    /** An omitted body is the same request as {@code {}}: every field takes its default. */
    private static ClusteringRequest orDefaults(@Nullable ClusteringRequest body) {
        return body == null ? new ClusteringRequest(null, null) : body;
    }

    /** An omitted {@code linkages} means all four; an explicit empty list is a malformed request. */
    private static List<String> linkageIds(ClusteringRequest request) {
        List<String> linkages = request.linkages();
        if (linkages == null) {
            return List.of();
        }
        if (linkages.isEmpty()) {
            throw new InvalidRequestException("linkages must not be empty when present");
        }
        return linkages;
    }

    /** See this class's Javadoc for why an unknown id is classified 400, not 404. */
    private static Representation parseRepresentation(@Nullable String id) {
        if (id == null) {
            return Representation.DEFAULT;
        }
        try {
            return Representation.fromId(id);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(ProblemType.UNKNOWN_REPRESENTATION, "unknown representation id: " + id);
        }
    }
}
