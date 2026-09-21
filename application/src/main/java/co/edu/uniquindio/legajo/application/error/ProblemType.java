package co.edu.uniquindio.legajo.application.error;

/**
 * The fixed RFC 9457 {@code type} URNs of TRD §6.6 ("Códigos de estado de error (fijados)"),
 * task A7. The TRD's table pins the same {@code type} regardless of HTTP status: the status
 * says <em>where</em> the bad value was (path → 404, body/query → 400), the {@code type}
 * always says <em>what</em> was wrong. This enum owns only that second axis — plain
 * enumeration values with no Spring/HTTP dependency, so it can be thrown from anywhere in
 * {@code application} without pulling infrastructure knowledge in; {@code
 * ProblemDetailExceptionHandler} (infrastructure) is the only place that turns {@link #urn()}
 * into a {@code java.net.URI} on the response body.
 *
 * <p>Deliberately does not cover the framework-detected cases (malformed JSON, missing
 * parameter, unsupported method/media type, unknown route, blank Bean Validation field) or an
 * unexpected 500: TRD §6.6 keeps those on Spring's standard {@code about:blank}, so no fixed
 * URN exists for them and none should be invented here.
 */
public enum ProblemType {

    /** 404 for `/corpus/{id}`'s path id; 400 for a compare/matrix body id or a trace query id. */
    UNKNOWN_DOCUMENT("unknown-document"),
    /** 404 for `/similarity/{algorithmId}/trace`'s path id; 400 for a compare/matrix body id. */
    UNKNOWN_ALGORITHM("unknown-algorithm"),
    /** 400 only — `representation` is never a path segment on any `/clustering*` endpoint. */
    UNKNOWN_REPRESENTATION("unknown-representation"),
    /** 400 only — `linkage`/`linkages` is never a path segment on any `/clustering*` endpoint. */
    UNKNOWN_LINKAGE("unknown-linkage"),
    /** 400 only — `/clustering/cut`'s free `k` is a body field, never a path segment. */
    INVALID_CUT("invalid-cut"),
    /** 400 only — `/similarity/matrix`'s selection is a body field, never a path segment. */
    INVALID_SELECTION("invalid-selection"),
    /** 503 only — NFR-QA-12's live-embedding degradation, never a client-classified status. */
    EMBEDDING_API_UNAVAILABLE("embedding-api-unavailable");

    private final String urnSuffix;

    ProblemType(String urnSuffix) {
        this.urnSuffix = urnSuffix;
    }

    /** {@code urn:legajo:problem:<name>} — a fixed URN, never a resolvable URL (TRD §6.6). */
    public String urn() {
        return "urn:legajo:problem:" + urnSuffix;
    }
}
