package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

/**
 * {@code embedding-local} capability:
 * cosine over cached MiniLM unit vectors, {@code normalizedScore = clamp(cos, 0, 1)},
 * {@code rawValue = cos}. The metric (dot product over unit vectors == cosine), the clamp,
 * and the angle are hand-written; no library implements them.
 *
 * <p>Goldens below are hand-computed (not read off a running implementation), using two
 * exact unit vectors that do not require floating-point approximation to build:
 *
 * <pre>
 * Golden 1 (3-4-5 triangle rotated into 3D):
 *   u = (0.6, 0.8, 0.0), v = (0.0, 0.6, 0.8) — both already unit length (||u|| = ||v|| = 1).
 *   dot = 0.6*0 + 0.8*0.6 + 0*0.8 = 0.48
 *   cosine = 0.48 (dot product of unit vectors == cosine)
 *   angle = degrees(acos(0.48)) = 61.31459798588108
 *   normalizedScore = clamp(0.48, 0, 1) = 0.48
 *
 * Golden 2 (opposite unit vectors, negative cosine):
 *   u = (1.0, 0.0, 0.0), v = (-1.0, 0.0, 0.0)
 *   dot = cosine = -1.0; angle = degrees(acos(-1.0)) = 180.0
 *   normalizedScore = clamp(-1.0, 0, 1) = 0.0 (negative cosines report similarity 0)
 * </pre>
 */
class EmbeddingLocalTest {

    private static final double TOLERANCE = 1e-9;
    private final EmbeddingLocal embeddingLocal = new EmbeddingLocal();

    @Test
    void goldenComputesTheHandComputedCosineAndClampedScore() {
        SimilarityInput a = inputFor(vector("d01", 0.6, 0.8, 0.0));
        SimilarityInput b = inputFor(vector("d02", 0.0, 0.6, 0.8));

        SimilarityResult result = embeddingLocal.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.rawValue()).isCloseTo(0.48, within(TOLERANCE));
        assertThat(result.normalizedScore()).isCloseTo(0.48, within(TOLERANCE));
        assertThat(result.degenerate()).isFalse();
    }

    @Test
    void aNegativeCosineIsClampedToZeroSimilarityButRawValueKeepsTheSign() {
        SimilarityInput a = inputFor(vector("d01", 1.0, 0.0, 0.0));
        SimilarityInput b = inputFor(vector("d02", -1.0, 0.0, 0.0));

        SimilarityResult result = embeddingLocal.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.rawValue()).isCloseTo(-1.0, within(TOLERANCE));
        assertThat(result.normalizedScore()).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void identicalVectorsScoreOne() {
        EmbeddingVector vector = vector("d01", 0.6, 0.8, 0.0);
        SimilarityInput a = inputFor(vector);
        SimilarityInput b = inputFor(vector);

        SimilarityResult result = embeddingLocal.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.rawValue()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void computedNanosIsMeasuredAndNonNegative() {
        SimilarityInput a = inputFor(vector("d01", 0.6, 0.8, 0.0));
        SimilarityInput b = inputFor(vector("d02", 0.0, 0.6, 0.8));

        SimilarityResult result = embeddingLocal.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.computedNanos()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void computeRejectsAnInputWithNoEmbeddingVector() {
        SimilarityInput a = new SimilarityInput("abstract a", List.of());
        SimilarityInput b = inputFor(vector("d02", 0.0, 0.6, 0.8));

        assertThatNullPointerException().isThrownBy(() -> embeddingLocal.compute(a, b, SimilarityContext.EMPTY));
    }

    @Test
    void traceReportsTheDotProductUnitVectorsCosineAngleAndProvenance() {
        EmbeddingVector vectorA = vector("d01", 0.6, 0.8, 0.0);
        EmbeddingVector vectorB = vector("d02", 0.0, 0.6, 0.8);
        SimilarityInput a = inputFor(vectorA);
        SimilarityInput b = inputFor(vectorB);

        EmbeddingLocalTrace trace = (EmbeddingLocalTrace) embeddingLocal.trace(a, b, SimilarityContext.EMPTY)
                .orElseThrow();

        assertThat(trace.algorithmId()).isEqualTo("embedding-local");
        assertThat(trace.provider()).isEqualTo("local");
        assertThat(trace.model()).isEqualTo("all-MiniLM-L6-v2");
        assertThat(trace.dimension()).isEqualTo(3);
        assertThat(trace.vectorA()).containsExactly(0.6, 0.8, 0.0);
        assertThat(trace.vectorB()).containsExactly(0.0, 0.6, 0.8);
        assertThat(trace.vectorAExcerpt()).containsExactly(0.6, 0.8, 0.0);
        assertThat(trace.vectorBExcerpt()).containsExactly(0.0, 0.6, 0.8);
        assertThat(trace.preNormL2A()).isCloseTo(vectorA.preNormL2(), within(TOLERANCE));
        assertThat(trace.preNormL2B()).isCloseTo(vectorB.preNormL2(), within(TOLERANCE));
        assertThat(trace.dotProduct()).isCloseTo(0.48, within(TOLERANCE));
        assertThat(trace.cosine()).isCloseTo(0.48, within(TOLERANCE));
        assertThat(trace.angleDegrees()).isCloseTo(61.31459798588108, within(TOLERANCE));
        assertThat(trace.normalizedScore()).isCloseTo(0.48, within(TOLERANCE));
    }

    @Test
    void traceExcerptIsCappedAtEightDimensionsForAHigherDimensionalVector() {
        double[] raw = new double[10];
        raw[0] = 1.0; // unit vector: a single 1.0 component.
        EmbeddingVector vectorA = new EmbeddingVector("d01", "local", "all-MiniLM-L6-v2", 1.0, boxed(raw));
        EmbeddingVector vectorB = new EmbeddingVector("d02", "local", "all-MiniLM-L6-v2", 1.0, boxed(raw));

        EmbeddingLocalTrace trace = (EmbeddingLocalTrace) embeddingLocal
                .trace(inputFor(vectorA), inputFor(vectorB), SimilarityContext.EMPTY)
                .orElseThrow();

        assertThat(trace.dimension()).isEqualTo(10);
        assertThat(trace.vectorAExcerpt()).hasSize(8);
        assertThat(trace.vectorAExcerpt()).isEqualTo(trace.vectorA().subList(0, 8));
    }

    private static List<Double> boxed(double[] values) {
        List<Double> boxed = new java.util.ArrayList<>(values.length);
        for (double value : values) {
            boxed.add(value);
        }
        return boxed;
    }

    private static EmbeddingVector vector(String documentId, double... components) {
        List<Double> values = new java.util.ArrayList<>(components.length);
        for (double component : components) {
            values.add(component);
        }
        double preNormL2 = EmbeddingVector.l2Norm(values); // already unit here, so preNormL2 == 1.
        return new EmbeddingVector(documentId, "local", "all-MiniLM-L6-v2", preNormL2, values);
    }

    private static SimilarityInput inputFor(EmbeddingVector vector) {
        return new SimilarityInput("raw abstract for " + vector.documentId(), List.of(), vector);
    }

    @Test
    void clamp01FailsClosedOnANonFiniteCosineInsteadOfSilentlyReturningNaN() {
        // Math.max(0.0, Math.min(1.0, NaN)) is NaN: a bare clamp does not reject a
        // non-finite cosine, it just propagates it as a NaN similarity to callers.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmbeddingLocal.clamp01(Double.NaN))
                .withMessageContaining("finite");
    }
}
