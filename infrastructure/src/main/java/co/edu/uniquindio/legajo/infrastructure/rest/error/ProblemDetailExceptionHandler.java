package co.edu.uniquindio.legajo.infrastructure.rest.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;

/**
 * Central RFC 9457 Problem Detail mapping for every {@code /api/v1/**} endpoint (TRD §6.6:
 * "Errores: Problem Detail de RFC 9457"). Spring Framework 7 / Boot 4 ships
 * {@link ProblemDetail} natively (task A3 constraint), so this advice only decides the
 * status per exception type; it never hand-rolls the error body shape.
 *
 * <p>Exception-to-status mapping used across A3's endpoints:
 * <ul>
 *   <li>{@link NoSuchElementException} → 404 — an unknown corpus id or algorithm id
 *       (thrown by {@code CorpusController}, {@code SimilarityAlgorithmRegistry.require},
 *       and {@code SimilarityService}'s document lookups).</li>
 *   <li>{@link IllegalArgumentException} → 400 — a validation failure (blank required
 *       field, an out-of-range matrix selection size, a duplicate document id).</li>
 *   <li>{@link MissingServletRequestParameterException} → 400 — a required query
 *       parameter is absent (e.g. {@code /similarity/{id}/trace}'s {@code documentIdA}/
 *       {@code documentIdB}).</li>
 *   <li>{@link MethodArgumentTypeMismatchException} → 400 — a query/path parameter Spring
 *       could not convert to its declared type.</li>
 *   <li>Anything else → 500, with a fixed, generic detail message. The real exception is
 *       logged server-side only: a response body must never leak a stack trace or an
 *       internal class name (task A3 constraint).</li>
 * </ul>
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "required parameter '%s' is missing".formatted(exception.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "parameter '%s' has an invalid value".formatted(exception.getName()));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled exception while serving a /api/v1 request", exception);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }
}
