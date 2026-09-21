package co.edu.uniquindio.legajo.infrastructure.rest.clustering;

import co.edu.uniquindio.legajo.application.clustering.ClusteringService;
import co.edu.uniquindio.legajo.application.clustering.LinkageEvaluationOnly;
import co.edu.uniquindio.legajo.application.clustering.LinkageRunResult;
import co.edu.uniquindio.legajo.application.error.InvalidRequestException;
import co.edu.uniquindio.legajo.application.error.ProblemType;
import co.edu.uniquindio.legajo.clustering.ClusterAssignment;
import co.edu.uniquindio.legajo.similarity.Representation;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * The three clustering endpoints of TRD §6.6 (feature doc task A4): {@code
 * POST /clustering}, {@code POST /clustering/evaluation}, {@code POST /clustering/cut}. Pure
 * adapter: every clustering rule (representation defaulting, linkage resolution, the
 * TAC-04 fixed-cut set, {@code cut}'s free-{@code k} range) lives in
 * {@link ClusteringService} (application); this class only parses the request DTOs, shapes
 * the responses, and lets {@link InvalidRequestException} (unknown linkage id, unknown
 * representation id, a missing or out-of-range {@code k} — all 400, task A7) bubble up to
 * {@code ProblemDetailExceptionHandler}.
 *
 * <p><b>Classifying an unknown {@code representation} id.</b> {@link Representation#fromId}
 * throws a raw {@link IllegalArgumentException} that would otherwise fall through to a 500
 * (A3b's error-classification fix, correctly, since a raw JDK exception is presumed a server
 * bug). This class instead maps that failure to {@link InvalidRequestException} (400,
 * {@code urn:legajo:problem:unknown-representation}): {@code representation} is not looked up
 * in any registry or catalogue — it is a plain computation-mode selector sent in the request
 * body, and TRD 1.3.7 §6.6 (task A7) fixes 404 to path-identified resources only.
 *
 * <p><b>Task A7: {@code linkage} is 400 too, for the same reason.</b> Before A7 an unknown
 * {@code linkage}/{@code linkages} id was 404; TRD 1.3.7 corrects this, since {@code linkage}
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
     * {@link Representation#DEFAULT}, {@code linkages} defaults to all four (TRD §6.6).
     */
    @PostMapping
    public List<LinkageResultResponse> run(@RequestBody ClusteringRequest request) {
        Representation representation = parseRepresentation(request.representation());
        List<String> linkageIds = request.linkages() == null ? List.of() : request.linkages();

        List<LinkageRunResult> results = clusteringService.run(representation, linkageIds);
        return results.stream().map(LinkageResultResponse::from).toList();
    }

    /** {@code POST /api/v1/clustering/evaluation}: the same computation as {@link #run},
     * evaluation block only. */
    @PostMapping("/evaluation")
    public List<LinkageEvaluationResponse> evaluation(@RequestBody ClusteringRequest request) {
        Representation representation = parseRepresentation(request.representation());
        List<String> linkageIds = request.linkages() == null ? List.of() : request.linkages();

        List<LinkageEvaluationOnly> results = clusteringService.evaluateOnly(representation, linkageIds);
        return results.stream().map(LinkageEvaluationResponse::from).toList();
    }

    /**
     * {@code POST /api/v1/clustering/cut}: the only endpoint accepting a free {@code k}.
     * {@code k}'s range check lives in {@link ClusteringService#cut} (the application
     * boundary, feature doc task A4); this method only guards against unboxing a {@code
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

        ClusterAssignment assignment = clusteringService.cut(representation, request.linkage(), request.k());
        return ClusterAssignmentResponse.from(assignment);
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
