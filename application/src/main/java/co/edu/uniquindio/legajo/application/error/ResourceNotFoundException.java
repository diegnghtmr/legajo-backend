package co.edu.uniquindio.legajo.application.error;

import java.util.NoSuchElementException;

/**
 * A {@code /api/v1/**} request identified a resource — a corpus document id, a similarity
 * algorithm id, a clustering linkage id — that does not exist (TRD §6.6). Mapped to
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
 * failure via {@link NoSuchElementException} (e.g. {@code
 * ClusteringServiceTest#runRejectsAnUnknownLinkageId}), and {@code
 * SimilarityAlgorithmRegistry} (domain) already throws a bare {@link NoSuchElementException}
 * for an unknown algorithm id — this subtype lets the application layer translate that
 * domain-thrown exception at the boundary without changing the domain. {@code
 * ProblemDetailExceptionHandler} now maps only this dedicated subtype to 404; a raw {@link
 * NoSuchElementException} — for example from an unguarded {@code Optional.get()} inside a
 * bug — is not an instance of this class, so it no longer matches that handler and instead
 * falls through to the generic 500 (advisory {@code R3-broad-exception-mapping}, feature doc
 * {@code rest-api.md}, task A3b).
 */
public class ResourceNotFoundException extends NoSuchElementException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
