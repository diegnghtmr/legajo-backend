package co.edu.uniquindio.legajo.infrastructure.rest.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.NoSuchElementException;

/**
 * Central RFC 9457 Problem Detail mapping for every {@code /api/v1/**} endpoint (TRD §6.6:
 * "Errores: Problem Detail de RFC 9457"). Spring Framework 7 / Boot 4 ships
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
 * <p>Mapping added here, on top of the base:
 * <ul>
 *   <li>{@link NoSuchElementException} → 404 — an unknown corpus id or algorithm id.</li>
 *   <li>{@link IllegalArgumentException} → 400 — a validation failure (blank required
 *       field, an out-of-range matrix selection size, a duplicate document id).</li>
 *   <li>Anything else → 500 with a fixed, generic detail. The real exception is logged
 *       server-side only: a response body never carries a stack trace or an internal
 *       class name.</li>
 * </ul>
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled exception while serving a /api/v1 request", exception);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }
}
