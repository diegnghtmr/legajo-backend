package co.edu.uniquindio.legajo.corpus;

import java.util.List;
import java.util.Objects;

/**
 * The complete set of {@link CorpusViolation}s found by {@link CorpusVerifier}. Every
 * applicable rule is checked regardless of earlier failures: any discrepancy is meant
 * to fail the overall verification run, not just the first one found — an early return
 * would describe only the outcome, so this verifier instead collects every violation
 * so the author can fix them all in one pass.
 */
public record CorpusVerificationResult(List<CorpusViolation> violations) {

    public CorpusVerificationResult {
        Objects.requireNonNull(violations, "violations");
        violations = List.copyOf(violations);
    }

    public boolean isValid() {
        return violations.isEmpty();
    }
}
