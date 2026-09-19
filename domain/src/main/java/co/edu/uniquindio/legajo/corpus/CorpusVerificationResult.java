package co.edu.uniquindio.legajo.corpus;

import java.util.List;
import java.util.Objects;

/**
 * The complete set of {@link CorpusViolation}s found by {@link CorpusVerifier}. Every
 * applicable rule is checked regardless of earlier failures (TRD §6.1, item 6: "cualquier
 * discrepancia hace fallar el script" describes the overall script outcome, not an
 * early return — this verifier collects every violation so the author can fix them
 * all in one pass).
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
