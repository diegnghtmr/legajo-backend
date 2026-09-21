package co.edu.uniquindio.legajo.application.error;

import java.util.Objects;

/**
 * An identifier lookup failed inside {@code application} for a value whose eventual HTTP
 * status this layer must not decide (task A7, TRD §6.6's "Códigos de estado de error"). Today
 * this covers a similarity algorithm id: {@code SimilarityService}'s registry lookup is
 * reused by {@code trace} (where the id is a path segment, {@code
 * urn:legajo:problem:unknown-algorithm} → 404) and by {@code compare}/{@code matrix} (where
 * the id lives in the request body, same {@code type} → 400). The lookup itself has no way to
 * know which of those two shapes the caller is — the same private helper serves all three
 * public methods — so it deliberately carries only the {@link ProblemType} and a message,
 * never a status: pushing that choice into the application layer would mean guessing, from
 * inside a plain Java method, which of two future controllers is calling it.
 *
 * <p>The infrastructure controller that owns the actual request shape makes the call instead:
 * {@code SimilarityController.trace} catches this and rethrows {@link ResourceNotFoundException}
 * (404), while {@code compare}/{@code matrix} catch it and rethrow {@link
 * InvalidRequestException} (400). An unrelated identifier kind that is never reused across a
 * path and a body — a corpus document id in {@code SimilarityService}, a clustering linkage id
 * — does not need this indirection: those helpers throw {@link InvalidRequestException}
 * directly, because every call site for them is already unambiguously a body/query context.
 *
 * <p>Deliberately a bare {@link RuntimeException}, not a subtype of {@link
 * InvalidRequestException} or {@link ResourceNotFoundException}: if a future call site forgets
 * to catch and reclassify it, it must not silently match either 4xx handler and must instead
 * fall through to the generic 500 — an unclassified identifier failure is safer reported as a
 * server bug than guessed into the wrong client-facing status.
 */
public final class UnknownIdentifierException extends RuntimeException {

    private final ProblemType type;

    public UnknownIdentifierException(ProblemType type, String message) {
        super(message);
        this.type = Objects.requireNonNull(type, "type");
    }

    public ProblemType type() {
        return type;
    }
}
