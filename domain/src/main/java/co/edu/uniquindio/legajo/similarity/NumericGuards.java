package co.edu.uniquindio.legajo.similarity;

/**
 * Shared finiteness guards for a compact constructor's tolerance and range invariants (TRD
 * §6.3, "Doble precisión"). A tolerance or range comparison of the shape {@code Math.abs(actual
 * - expected) > tolerance} is silently {@code false} when either operand is {@code NaN} — a
 * NaN comparison is never {@code >}, {@code <}, or {@code ==} anything, including itself —
 * so every compact constructor that checks a derived {@code double} against an expected
 * value or a fixed range must reject non-finite input explicitly instead of relying on the
 * bare comparison alone. This bug shape was first found and fixed ad hoc in
 * {@link EmbeddingVector} (an all-zero cached vector renormalizes to {@code NaN} per
 * component, which the old {@code Math.abs(NaN - 1.0) > tolerance} guard let through
 * silently); centralizing the rule here means the next capability's compact constructor
 * cannot forget it.
 *
 * <p><b>Deliberately public and shared across the domain's algorithm packages (RF2, TRD
 * §6.4/§6.5).</b> {@code clustering} and {@code evaluation} need this exact rule for their
 * own compact constructors — {@link co.edu.uniquindio.legajo.clustering.DistanceMatrix}'s
 * symmetry/zero-diagonal/non-negativity checks, the Lance–Williams linkage heights, and the
 * cophenetic/silhouette/Davies–Bouldin metrics all compare a derived {@code double} against
 * a tolerance or a fixed range, exactly the shape this class exists to guard. Duplicating
 * the rule in a second package would defeat the reason it was centralized here in the first
 * place: the next capability's compact constructor could simply forget it again. Kept in
 * {@code similarity} rather than moved to a new shared package: {@code clustering} already
 * depends on {@code similarity} for {@link EmbeddingVector} and {@link TfIdfCorpusIndex}, so
 * this adds no new coupling, and a new package would fall outside the exact three packages
 * ({@code similarity}, {@code clustering}, {@code evaluation}) the per-package coverage gate
 * discovers by name.
 */
public final class NumericGuards {

    private NumericGuards() {
    }

    /** Throws {@link IllegalArgumentException} unless {@code value} is finite. */
    public static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, was " + value);
        }
    }

    /** Throws {@link IllegalArgumentException} unless {@code value} is finite and non-negative. */
    public static void requireNonNegativeFinite(double value, String name) {
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
    public static boolean isOutOfTolerance(double value, double expected, double tolerance) {
        return !Double.isFinite(value) || !Double.isFinite(expected) || Math.abs(value - expected) > tolerance;
    }

    /**
     * True when {@code value} is not finite, or falls outside {@code [min - tolerance, max +
     * tolerance]}.
     */
    public static boolean isOutOfRange(double value, double min, double max, double tolerance) {
        return !Double.isFinite(value) || value < min - tolerance || value > max + tolerance;
    }
}
