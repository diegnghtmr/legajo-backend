package co.edu.uniquindio.legajo.application.error;

import java.util.Objects;
import java.util.Optional;

/**
 * A {@code /api/v1/**} request failed validation the application layer is responsible for
 * performing before touching the domain — an out-of-range matrix selection size, a
 * duplicate document id, a blank required field (TRD §6.6). Mapped to
 * {@code 400 Bad Request} by {@code ProblemDetailExceptionHandler}, with {@link
 * #getMessage()} surfaced as the Problem Detail's {@code detail}.
 *
 * <p><b>Why "Invalid" and not "BadRequest" or "Validation".</b> "Invalid request" describes
 * the fault from the request's point of view (what is wrong), matching the vocabulary the
 * REST layer already uses for this case (TRD §6.6, {@code requireNonBlank} in {@code
 * SimilarityController}); "BadRequest" would name the HTTP status rather than the fault, and
 * a name coupled to a status code stops describing the fault the moment the mapping ever
 * needs to change. "Validation" was considered but reads as the mechanism, not the outcome.
 *
 * <p><b>Why this extends {@link IllegalArgumentException} rather than a bare {@link
 * RuntimeException}.</b> Call sites and unit tests across this codebase already assert a
 * validation failure via {@code IllegalArgumentException} (e.g. {@code
 * SimilarityServiceTest#matrixRejectsASelectionSmallerThanThree}); extending it keeps that
 * behavior contract intact. What changes is the REST mapping: {@code
 * ProblemDetailExceptionHandler} used to map every {@link IllegalArgumentException} to 400,
 * including one thrown by an unrelated server-side bug. It now maps only this dedicated
 * subtype, so a raw {@link IllegalArgumentException} — not an instance of this class — no
 * longer matches that handler and instead falls through to the generic 500, which is the
 * fix task A3b makes (advisory {@code R3-broad-exception-mapping}, feature doc
 * {@code rest-api.md}).
 *
 * <p><b>{@link ProblemType} (task A7, TRD §6.6's "Códigos de estado de error").</b> Every
 * fixed-URN row of the TRD's table that answers 400 (unknown algorithm/document id in a body
 * or query, unknown representation, unknown linkage, an out-of-range cut {@code k}, an
 * invalid matrix selection) carries a {@link ProblemType} so {@code
 * ProblemDetailExceptionHandler} can set the Problem Detail's {@code type}. A validation
 * failure the TRD does not name with a fixed URN (a blank required field, a missing body
 * field) uses the single-argument constructor and keeps {@code type} absent, which leaves
 * the Problem Detail's {@code type} at Spring's own default ({@code about:blank}) — exactly
 * the framework-error behavior the TRD reserves for cases it does not classify.
 */
public class InvalidRequestException extends IllegalArgumentException {

    private final ProblemType type;

    public InvalidRequestException(String message) {
        this(null, message);
    }

    public InvalidRequestException(ProblemType type, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.type = type;
    }

    /** Absent for a validation failure the TRD's fixed-URN table does not name. */
    public Optional<ProblemType> type() {
        return Optional.ofNullable(type);
    }
}
