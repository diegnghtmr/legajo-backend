package co.edu.uniquindio.legajo.similarity;

/**
 * Classification of one backtrace step in a {@link DpMatrixTrace} (TRD §6.3). Levenshtein
 * (S2) classifies every step as one of {@link #MATCH}, {@link #SUBSTITUTION},
 * {@link #INSERTION}, or {@link #DELETION} — insertion and deletion are directional
 * because Levenshtein's edit script distinguishes "extra token in B" from "extra token in
 * A". Needleman–Wunsch (S3) instead classifies a step as {@link #MATCH},
 * {@link #MISMATCH}, or {@link #GAP} — its alignment view does not need the gap's
 * direction, only that a gap was opened. Both algorithms resolve backtrace ties with the
 * same fixed order: diagonal, then up, then left.
 */
public enum DpOperationKind {
    MATCH,
    SUBSTITUTION,
    INSERTION,
    DELETION,
    MISMATCH,
    GAP
}
