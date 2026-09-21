package co.edu.uniquindio.legajo.application.error;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@code /api/v1/**} request identified a resource by a **path** segment that does not
 * exist (TRD §6.6, task A7: 404 is fixed to path-identified resources only —
 * {@code GET /corpus/{id}} and {@code GET /similarity/{algorithmId}/trace}). Mapped to
 * {@code 404 Not Found} by {@code ProblemDetailExceptionHandler}, with {@link #getMessage()}
 * surfaced as the Problem Detail's {@code detail}.
 *
 * <p><b>Why "ResourceNotFound" and not "NotFound" or "Unknown".</b> "Resource" names what is
 * missing (an addressable REST resource identified by an id in the request), matching RFC
 * 9457/HTTP vocabulary for the 404 status this maps to; a bare "NotFound" reads as a status
 * name rather than a fault, and "Unknown" (the adjective this codebase's own messages already
 * use, e.g. {@code "no similarity algorithm registered with id: ..."}) was rejected only
 * because "UnknownXException" does not read as a REST-facing type the way
 * "ResourceNotFoundException" does.
 *
 * <p><b>Why this extends {@link NoSuchElementException} rather than a bare {@link
 * RuntimeException}.</b> Mirrors {@code InvalidRequestException} extending {@link
 * IllegalArgumentException}: existing call sites and unit tests already assert a lookup
 * failure via {@link NoSuchElementException}, and {@code SimilarityAlgorithmRegistry} (domain)
 * already throws a bare {@link NoSuchElementException} for an unknown algorithm id — this
 * subtype lets the application layer translate that domain-thrown exception at the boundary
 * without changing the domain. {@code ProblemDetailExceptionHandler} now maps only this
 * dedicated subtype to 404; a raw {@link NoSuchElementException} — for example from an
 * unguarded {@code Optional.get()} inside a bug — is not an instance of this class, so it no
 * longer matches that handler and instead falls through to the generic 500 (advisory
 * {@code R3-broad-exception-mapping}, feature doc {@code rest-api.md}, task A3b).
 *
 * <p><b>404 is now path-only (task A7).</b> Before task A7 this type also covered an unknown
 * algorithm/document id supplied in a request body, which TRD 1.3.7 §6.6 fixes to 400
 * instead: RFC 9110 defines 404 as the absence of the target *resource* named by the URI, and
 * every one of these endpoints' URIs exists — only a value inside the body was wrong. The two
 * remaining path-identified cases are thrown directly by {@code CorpusController} (its own
 * {@code Optional} lookup) and by {@code SimilarityController.trace} (which catches {@code
 * UnknownIdentifierException} from {@code SimilarityService} and rethrows it as this type,
 * since the service itself cannot know its {@code algorithmId} parameter is a path segment —
 * see {@link UnknownIdentifierException}'s Javadoc).
 *
 * <p><b>{@link ProblemType} (task A7).</b> Both fixed-URN 404 rows of the TRD's table
 * ({@code unknown-document} for {@code GET /corpus/{id}}, {@code unknown-algorithm} for
 * {@code GET /similarity/{algorithmId}/trace}) pass one so {@code
 * ProblemDetailExceptionHandler} can set the Problem Detail's {@code type}; a 404 the TRD does
 * not fix with a URN uses the single-argument constructor and keeps {@code type} absent
 * (Spring's own default, {@code about:blank}).
 */
public class ResourceNotFoundException extends NoSuchElementException {

    private final ProblemType type;

    public ResourceNotFoundException(String message) {
        this(null, message);
    }

    public ResourceNotFoundException(ProblemType type, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.type = type;
    }

    /** Absent for a 404 the TRD's fixed-URN table does not name. */
    public Optional<ProblemType> type() {
        return Optional.ofNullable(type);
    }
}
