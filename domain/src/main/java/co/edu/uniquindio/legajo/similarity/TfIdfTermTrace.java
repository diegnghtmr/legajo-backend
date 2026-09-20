package co.edu.uniquindio.legajo.similarity;

import java.util.Objects;

/**
 * One term's row in a {@link TfIdfCosineTrace} (TRD §6.3: "por término f(t,d), tf, df, idf,
 * peso bruto y peso normalizado"): the raw frequency of {@code term} in each compared
 * document, the corpus-wide document frequency and idf, the raw weight {@code w(t,d) =
 * tf(t,d) · idf(t)}, and the L2-normalized weight (computed against the pair's raw norm,
 * TRD §6.3's explicit "segundo paso").
 *
 * <p>Validated invariants: no negative frequency/df; a row requires {@code frequencyA > 0}
 * or {@code frequencyB > 0} (a term absent from both sides carries {@code tf = w = 0} on
 * both sides and is not informative — {@link TfIdfCosine} lists only terms present in at
 * least one of the two compared documents, see its class Javadoc); and {@code tf == 0}
 * exactly when the corresponding {@code frequency == 0} — {@code tf(t,d) = 1 + ln f(t,d)}
 * is always {@code >= 1} for any integer {@code f >= 1}, so a positive frequency can never
 * legitimately carry a zero tf, and vice versa. The raw-weight formula itself
 * ({@code rawWeight == tf · idf}) is validated too.
 */
public record TfIdfTermTrace(
        String term,
        int frequencyA,
        int frequencyB,
        int documentFrequency,
        double tfA,
        double tfB,
        double idf,
        double rawWeightA,
        double rawWeightB,
        double normalizedWeightA,
        double normalizedWeightB) {

    private static final double TOLERANCE = 1e-9;

    public TfIdfTermTrace {
        Objects.requireNonNull(term, "term");
        if (term.isEmpty()) {
            throw new IllegalArgumentException("term must not be empty");
        }
        if (frequencyA < 0) {
            throw new IllegalArgumentException("frequencyA must not be negative, was " + frequencyA);
        }
        if (frequencyB < 0) {
            throw new IllegalArgumentException("frequencyB must not be negative, was " + frequencyB);
        }
        if (documentFrequency < 0) {
            throw new IllegalArgumentException(
                    "documentFrequency must not be negative, was " + documentFrequency);
        }
        if (frequencyA == 0 && frequencyB == 0) {
            throw new IllegalArgumentException(
                    "a term row requires a positive frequency in A or B, term was '" + term + "'");
        }
        // R3-tfidfterm-partial-migration: tfA/tfB/idf used to be validated only implicitly,
        // through the rawWeight == tf*idf checks below (a non-finite one of these propagates
        // into rawWeightA/rawWeightB and gets rejected there) — finishing the migration onto
        // NumericGuards validates each field directly, with its own attributable message.
        NumericGuards.requireFinite(tfA, "tfA");
        NumericGuards.requireFinite(tfB, "tfB");
        NumericGuards.requireFinite(idf, "idf");
        if ((frequencyA == 0) != (tfA == 0.0)) {
            throw new IllegalArgumentException(
                    "tfA must be 0 exactly when frequencyA is 0 (frequencyA=%d, tfA=%s)"
                            .formatted(frequencyA, tfA));
        }
        if ((frequencyB == 0) != (tfB == 0.0)) {
            throw new IllegalArgumentException(
                    "tfB must be 0 exactly when frequencyB is 0 (frequencyB=%d, tfB=%s)"
                            .formatted(frequencyB, tfB));
        }
        double expectedRawWeightA = tfA * idf;
        if (NumericGuards.isOutOfTolerance(rawWeightA, expectedRawWeightA, TOLERANCE)) {
            throw new IllegalArgumentException(
                    "rawWeightA must equal tfA * idf (%.12f), was %.12f".formatted(expectedRawWeightA, rawWeightA));
        }
        double expectedRawWeightB = tfB * idf;
        if (NumericGuards.isOutOfTolerance(rawWeightB, expectedRawWeightB, TOLERANCE)) {
            throw new IllegalArgumentException(
                    "rawWeightB must equal tfB * idf (%.12f), was %.12f".formatted(expectedRawWeightB, rawWeightB));
        }
    }
}
