package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Structural invariants of {@link TfIdfCosineTrace} (TRD §6.3: per-term rows, dot product,
 * both raw norms, cosine, angle°). TRD's fixed-formula paragraph states "el coseno es el
 * producto punto de los vectores normalizados", so {@code cosine} must equal {@code
 * dotProduct} (both are the same number, kept as two labeled trace fields per the
 * enumeration in TRD §6.3 / PRD HU-1.3, which lists "producto punto" and "coseno" as
 * separate evidence rows even though the fixed formula makes them numerically identical
 * once the dot product is taken over the L2-normalized vectors).
 *
 * <p>Each isolated-violation test perturbs exactly one field of an otherwise fully
 * arithmetically consistent single-term fixture ({@code cat}: raw weight 1.0 on both sides,
 * so rawNorm = 1.0, normalized weight = 1.0 on both sides, dot = cosine = 1.0, angle = 0°),
 * so the exception can be attributed to the intended check.
 */
class TfIdfCosineTraceTest {

    // Self-consistent single-term fixture: rawNormA = rawNormB = 1.0 (one term, weight 1.0);
    // normalizedWeight = rawWeight / rawNorm = 1.0; dot = cosine = 1.0; angle = acos(1) = 0°.
    private static final TfIdfTermTrace CAT_SINGLE = new TfIdfTermTrace(
            "cat", 1, 1, 2, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);

    // Two-term fixture (cat, dog) with idf(dog) = sqrt(3) chosen so the arithmetic is exact:
    // rawWeightA = {cat: 1.0, dog: sqrt(3)} -> rawNormA = sqrt(1 + 3) = 2.0
    // rawWeightB = {cat: 1.0, dog: 0.0}     -> rawNormB = sqrt(1 + 0) = 1.0
    // normalizedA = {cat: 0.5, dog: sqrt(3)/2}, normalizedB = {cat: 1.0, dog: 0.0}
    // dot = 0.5*1.0 + (sqrt(3)/2)*0.0 = 0.5 = cosine; angle = acos(0.5) = 60°
    private static final TfIdfTermTrace CAT = new TfIdfTermTrace(
            "cat", 1, 1, 2, 1.0, 1.0, 1.0, 1.0, 1.0, 0.5, 1.0);
    private static final TfIdfTermTrace DOG = new TfIdfTermTrace(
            "dog", 1, 0, 1, 1.0, 0.0, 1.7320508075688772, 1.7320508075688772, 0.0,
            0.8660254037844386, 0.0);

    @Test
    void acceptsAConsistentTraceWithTwoTerms() {
        assertThatNoException().isThrownBy(() -> new TfIdfCosineTrace(
                "tfidf-cosine", 2, List.of(CAT, DOG), 0.5, 2.0, 1.0, 0.5, 60.0));
    }

    @Test
    void acceptsAConsistentTraceWithOneTerm() {
        assertThatNoException().isThrownBy(() -> new TfIdfCosineTrace(
                "tfidf-cosine", 2, List.of(CAT_SINGLE), 1.0, 1.0, 1.0, 1.0, 0.0));
    }

    @Test
    void rejectsTermsOutOfAlphabeticalOrder() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", 2, List.of(DOG, CAT), 0.5, 2.0, 1.0, 0.5, 60.0))
                .withMessageContaining("terms");
    }

    @Test
    void rejectsANegativeCorpusSize() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", -1, List.of(CAT_SINGLE), 1.0, 1.0, 1.0, 1.0, 0.0))
                .withMessageContaining("corpusSize");
    }

    @Test
    void rejectsADotProductInconsistentWithTheNormalizedWeights() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", 2, List.of(CAT_SINGLE), 0.9, 1.0, 1.0, 0.9, 25.84193))
                .withMessageContaining("dotProduct");
    }

    @Test
    void rejectsARawNormAInconsistentWithTheRawWeights() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", 2, List.of(CAT_SINGLE), 1.0, 99.0, 1.0, 1.0, 0.0))
                .withMessageContaining("rawNormA");
    }

    @Test
    void rejectsARawNormBInconsistentWithTheRawWeights() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", 2, List.of(CAT_SINGLE), 1.0, 1.0, 99.0, 1.0, 0.0))
                .withMessageContaining("rawNormB");
    }

    @Test
    void rejectsACosineInconsistentWithTheDotProduct() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", 2, List.of(CAT_SINGLE), 1.0, 1.0, 1.0, 0.1, 0.0))
                .withMessageContaining("cosine");
    }

    @Test
    void rejectsAnAngleInconsistentWithArccosineOfTheCosine() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", 2, List.of(CAT_SINGLE), 1.0, 1.0, 1.0, 1.0, 45.0))
                .withMessageContaining("angleDegrees");
    }

    @Test
    void acceptsTheDegenerateBothEmptyConvention() {
        assertThatNoException().isThrownBy(() -> new TfIdfCosineTrace(
                "tfidf-cosine", 0, List.of(), 1.0, 0.0, 0.0, 1.0, 0.0));
    }

    @Test
    void rejectsANonZeroRawNormAWhenTermsIsEmpty() {
        // With no per-term evidence, both raw norms must be exactly 0 (sqrt of an empty
        // sum) — this is currently unchecked whenever terms is empty.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", 0, List.of(), 1.0, 5.0, 0.0, 1.0, 0.0))
                .withMessageContaining("rawNormA");
    }

    @Test
    void rejectsANonZeroRawNormBWhenTermsIsEmpty() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", 0, List.of(), 1.0, 0.0, 5.0, 1.0, 0.0))
                .withMessageContaining("rawNormB");
    }

    @Test
    void rejectsADotProductThatIsNotOneOfTheFixedDegenerateConventionValuesWhenTermsIsEmpty() {
        // R3-tfidf-empty-terms-dotproduct: with terms empty, the dotProduct==sum-over-terms
        // check is skipped entirely (there is nothing to sum), so dotProduct/cosine/
        // angleDegrees were only checked against EACH OTHER for internal consistency, never
        // against the two fixed TRD §6.3 convention values (1.0 both-empty, 0.0 one-empty).
        // 0.5/60 degrees is internally consistent (cosine==dotProduct,
        // angle==degrees(acos(cosine))) but is not a valid degenerate value.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", 0, List.of(), 0.5, 0.0, 0.0, 0.5, 60.0))
                .withMessageContaining("dotProduct");
    }

    @Test
    void acceptsTheDegenerateExactlyOneEmptyConvention() {
        // No index is consulted for this short-circuited case (TfIdfCosine's degenerate
        // branches never touch the corpus index), so no genuine per-term evidence exists
        // for either side: both raw norms are 0 (sqrt of an empty sum), matching the empty
        // terms list, exactly like the both-empty case below.
        assertThatNoException().isThrownBy(() -> new TfIdfCosineTrace(
                "tfidf-cosine", 1, List.of(), 0.0, 0.0, 0.0, 0.0, 90.0));
    }

    // Each of the five tolerance-comparison invariants below used to accept a NaN in the
    // checked field silently: Math.abs(NaN - expected) > tolerance is false, so the old
    // bare comparison never rejected it.

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteDotProduct(double nonFinite) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", 2, List.of(CAT_SINGLE), nonFinite, 1.0, 1.0, 1.0, 0.0))
                .withMessageContaining("dotProduct");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteRawNormA(double nonFinite) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", 2, List.of(CAT_SINGLE), 1.0, nonFinite, 1.0, 1.0, 0.0))
                .withMessageContaining("rawNormA");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteRawNormB(double nonFinite) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", 2, List.of(CAT_SINGLE), 1.0, 1.0, nonFinite, 1.0, 0.0))
                .withMessageContaining("rawNormB");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteCosine(double nonFinite) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", 2, List.of(CAT_SINGLE), 1.0, 1.0, 1.0, nonFinite, 0.0))
                .withMessageContaining("cosine");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteAngleDegrees(double nonFinite) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TfIdfCosineTrace(
                        "tfidf-cosine", 2, List.of(CAT_SINGLE), 1.0, 1.0, 1.0, 1.0, nonFinite))
                .withMessageContaining("angleDegrees");
    }
}
