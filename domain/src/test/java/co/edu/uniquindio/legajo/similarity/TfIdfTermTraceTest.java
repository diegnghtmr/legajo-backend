package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Structural invariants of one {@link TfIdfTermTrace} row (TRD §6.3: "por término f(t,d),
 * tf, df, idf, peso bruto y peso normalizado"): non-negative frequencies/df, a term present
 * in at least one of the two documents (a row for a term absent from both would be all
 * zeros and carries no evidence), and {@code tf == 0} exactly when {@code frequency == 0}
 * ({@code tf(t,d) = 1 + ln f(t,d)} is always {@code >= 1} for any integer {@code f >= 1}).
 */
class TfIdfTermTraceTest {

    @Test
    void acceptsAConsistentRow() {
        assertThatNoException().isThrownBy(() -> new TfIdfTermTrace(
                "cat", 1, 1, 3, 1.0, 1.0, 1.2231435513142097,
                1.2231435513142097, 1.2231435513142097, 0.5, 0.5));
    }

    @Test
    void rejectsABlankTerm() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfTermTrace("", 1, 0, 1, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0, 0.0))
                .withMessageContaining("term");
    }

    @Test
    void rejectsANegativeFrequencyA() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfTermTrace("cat", -1, 1, 1, 0.0, 1.0, 1.0, 0.0, 1.0, 0.0, 1.0))
                .withMessageContaining("frequencyA");
    }

    @Test
    void rejectsANegativeFrequencyB() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfTermTrace("cat", 1, -1, 1, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0, 0.0))
                .withMessageContaining("frequencyB");
    }

    @Test
    void rejectsANegativeDocumentFrequency() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfTermTrace("cat", 1, 0, -1, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0, 0.0))
                .withMessageContaining("documentFrequency");
    }

    @Test
    void rejectsARowWithZeroFrequencyOnBothSides() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfTermTrace("cat", 0, 0, 1, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0))
                .withMessageContaining("frequency");
    }

    @Test
    void rejectsATfAInconsistentWithFrequencyA() {
        // frequencyA=0 must give tfA=0, and frequencyA>0 must give tfA>0.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfTermTrace("cat", 0, 1, 1, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0))
                .withMessageContaining("tfA");
    }

    @Test
    void rejectsATfBInconsistentWithFrequencyB() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfTermTrace("cat", 1, 0, 1, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0))
                .withMessageContaining("tfB");
    }

    // R3-tfidfterm-partial-migration: rawWeightA/rawWeightB were already migrated onto
    // NumericGuards (below), but tfA/tfB/idf themselves were only ever implicitly protected
    // (a non-finite one of these propagates into rawWeightA/rawWeightB via tf*idf, which the
    // rawWeight checks then reject) — finishing the migration validates each field directly,
    // with its own attributable message, rather than relying on that indirection.

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteIdf(double nonFinite) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfTermTrace(
                        "cat", 1, 1, 3, 1.0, 1.0, nonFinite, 1.2231435513142097, 1.2231435513142097, 0.5, 0.5))
                .withMessageContaining("idf must be finite");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteTfA(double nonFinite) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfTermTrace(
                        "cat", 1, 1, 3, nonFinite, 1.0, 1.2231435513142097, 1.2231435513142097,
                        1.2231435513142097, 0.5, 0.5))
                .withMessageContaining("tfA must be finite");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteTfB(double nonFinite) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfTermTrace(
                        "cat", 1, 1, 3, 1.0, nonFinite, 1.2231435513142097, 1.2231435513142097,
                        1.2231435513142097, 0.5, 0.5))
                .withMessageContaining("tfB must be finite");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteRawWeightA(double nonFinite) {
        // Math.abs(NaN - expected) > tolerance is false, so the old guard let a NaN
        // rawWeightA through silently instead of failing closed.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfTermTrace(
                        "cat", 1, 1, 3, 1.0, 1.0, 1.2231435513142097,
                        nonFinite, 1.2231435513142097, 0.5, 0.5))
                .withMessageContaining("rawWeightA");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteRawWeightB(double nonFinite) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfTermTrace(
                        "cat", 1, 1, 3, 1.0, 1.0, 1.2231435513142097,
                        1.2231435513142097, nonFinite, 0.5, 0.5))
                .withMessageContaining("rawWeightB");
    }
}
