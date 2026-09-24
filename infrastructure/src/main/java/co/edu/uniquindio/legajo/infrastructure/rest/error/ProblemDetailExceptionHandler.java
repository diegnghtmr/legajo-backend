package co.edu.uniquindio.legajo.infrastructure.rest.error;

import co.edu.uniquindio.legajo.application.error.InvalidRequestException;
import co.edu.uniquindio.legajo.application.error.ProblemType;
import co.edu.uniquindio.legajo.application.error.ResourceNotFoundException;
import co.edu.uniquindio.legajo.infrastructure.embedding.EmbeddingApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

/**
 * Central RFC 9457 Problem Detail mapping for every {@code /api/v1/**} endpoint: every error
 * response is a Problem Detail per RFC 9457. Spring Framework 7 / Boot 4 ships
 * {@link ProblemDetail} natively, so this advice only decides the status per exception type;
 * it never hand-rolls the error body shape.
 *
 * <p><b>Why this extends {@link ResponseEntityExceptionHandler}.</b> An unscoped
 * {@code @ExceptionHandler(Exception.class)} is consulted before Spring MVC's own resolver,
 * so on its own it swallows the client mistakes Spring already knows how to classify: a
 * malformed or missing JSON body, an unsupported HTTP method, an unsupported
 * {@code Content-Type}, an unknown route, a missing or unconvertible parameter. Each would
 * answer 500 and be logged at ERROR as if the server had failed. The base class maps all of
 * those to their proper 4xx status as Problem Details, and because its handlers name those
 * exact exception types they win over the catch-all below. That is also why this class no
 * longer declares its own handlers for missing or mistyped parameters: the base already
 * owns those types, and two handlers for the same type in one advice fail at startup.
 *
 * <p><b>Why {@link IllegalArgumentException} and {@link java.util.NoSuchElementException}
 * are not mapped here.</b> Both are plain JDK exception types that any code can throw,
 * including code that is simply buggy. Mapping them broadly meant a server-side bug that
 * happened to throw either one was reported to the client as "your request was invalid" or
 * "the resource does not exist", {@link Exception#getMessage()} and all, instead of the
 * honest 500. Only the dedicated {@link InvalidRequestException}/{@link
 * ResourceNotFoundException} subtypes — introduced specifically to mean "the request failed
 * validation" and "the requested resource does not exist" — are mapped below. A raw {@link
 * IllegalArgumentException} or {@link java.util.NoSuchElementException} that is not an
 * instance of one of those subtypes no longer matches either handler and falls through to
 * the catch-all, which is the correct outcome: the server, not the client, is at fault.
 *
 * <p>Mapping added here, on top of the base:
 * <ul>
 *   <li>{@link ResourceNotFoundException} → 404 — an unknown corpus, algorithm, or
 *       linkage id.</li>
 *   <li>{@link InvalidRequestException} → 400 — a validation failure (blank required
 *       field, an out-of-range matrix selection size, a duplicate document id, an unknown
 *       representation id, an out-of-range clustering cut {@code k}).</li>
 *   <li>{@link EmbeddingApiException} → 503 — a live-embedding degradation: a network
 *       failure or missing API key on a path that needs a live embedding refresh must never
 *       look like a server bug (500) to the client, because the cached-mode demo keeps
 *       working regardless. See this class's Javadoc addendum below for what this mapping
 *       covers.</li>
 *   <li>Anything else → 500 with a fixed, generic detail. The real exception is logged
 *       server-side only: a response body never carries a stack trace or an internal
 *       class name.</li>
 * </ul>
 *
 * <p><b>When this mapping actually triggers.</b> With {@code legajo.embedding-provider=live},
 * {@code DomainConfiguration} backs the {@code apiEmbeddingRepository} bean with {@code
 * LiveApiEmbeddingRepository} instead of the versioned {@code embeddings-openai.json} cache.
 * {@code SimilarityService}, {@code ClusteringService}, and {@code EmbeddingsService} each
 * call {@code EmbeddingRepository#load()} once per request, so in live mode that call
 * synchronously fetches any not-yet-cached document's vector from the configured
 * OpenAI-compatible embedding endpoint over the network, through {@code
 * OpenAiCompatibleEmbedder}, at request time. {@link EmbeddingApiException} — the only
 * exception this handler maps to 503 — is exactly what that live call throws on a network
 * failure, a missing or blank API key, or an unexpected response shape, so a real request
 * can genuinely hit this mapping whenever the server runs in live mode. In the default
 * {@code cached} mode this path never triggers, because {@code JsonEmbeddingRepository}
 * only reads the local, precomputed cache file and never calls the network.
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        exception.type().ifPresent(type -> problem.setType(URI.create(type.urn())));
        return problem;
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleBadRequest(InvalidRequestException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        exception.type().ifPresent(type -> problem.setType(URI.create(type.urn())));
        return problem;
    }

    /** A live-embedding failure is a degraded dependency, not a client mistake or a server
     * bug — 503, with the real cause logged server-side only. */
    @ExceptionHandler(EmbeddingApiException.class)
    public ProblemDetail handleEmbeddingApiUnavailable(EmbeddingApiException exception) {
        log.warn("Live embedding API unavailable (degraded dependency)", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "The live embedding API is currently unavailable.");
        problem.setType(URI.create(ProblemType.EMBEDDING_API_UNAVAILABLE.urn()));
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled exception while serving a /api/v1 request", exception);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }
}
