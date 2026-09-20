package co.edu.uniquindio.legajo.similarity;

/**
 * Shared finiteness guards for this package's record invariants (TRD §6.3, "Doble
 * precisión"). A tolerance or range comparison of the shape {@code Math.abs(actual -
 * expected) > tolerance} is silently {@code false} when either operand is {@code NaN} — a
 * NaN comparison is never {@code >}, {@code <}, or {@code ==} anything, including itself —
 * so every compact constructor that checks a derived {@code double} against an expected
 * value or a fixed range must reject non-finite input explicitly instead of relying on the
 * bare comparison alone. This bug shape was first found and fixed ad hoc in
 * {@link EmbeddingVector} (an all-zero cached vector renormalizes to {@code NaN} per
 * component, which the old {@code Math.abs(NaN - 1.0) > tolerance} guard let through
 * silently); centralizing the rule here means the next similarity capability's compact
 * constructor cannot forget it.
 */
final class NumericGuards {

    private NumericGuards() {
    }

    /** Throws {@link IllegalArgumentException} unless {@code value} is finite. */
    static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, was " + value);
        }
    }

    /** Throws {@link IllegalArgumentException} unless {@code value} is finite and non-negative. */
    static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be a non-negative finite number, was " + value);
        }
    }

    /**
     * True when {@code value} or {@code expected} is not finite, or when {@code value}
     * differs from {@code expected} by more than {@code tolerance}. Both operands' finiteness
     * is checked — not just {@code value}'s — because {@code expected} is sometimes itself a
     * raw field being compared against another field (e.g. {@code cosine} against {@code
     * dotProduct}), not a value derived purely from already-validated inputs.
     */
    static boolean isOutOfTolerance(double value, double expected, double tolerance) {
        return !Double.isFinite(value) || !Double.isFinite(expected) || Math.abs(value - expected) > tolerance;
    }

    /**
     * True when {@code value} is not finite, or falls outside {@code [min - tolerance, max +
     * tolerance]}.
     */
    static boolean isOutOfRange(double value, double min, double max, double tolerance) {
        return !Double.isFinite(value) || value < min - tolerance || value > max + tolerance;
    }
}
