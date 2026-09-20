package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

/**
 * {@code embedding-api} capability (TRD §6.3, "Capacidades de embedding (fijadas)"; ADR-015):
 * Euclidean distance over cached {@code gemini-embedding-2-preview} unit vectors,
 * {@code normalizedScore = clamp(1 - d/sqrt(2), 0, 1)}, {@code rawValue = d}. The metric
 * (sum of squared differences, the square root, and the clamp) is hand-written (TRD §3.3).
 *
 * <p>Goldens below are hand-computed (not read off a running implementation), reusing
 * {@code embedding-local}'s exact unit vectors so both capabilities' trace/case-study pages
 * can be cross-checked against each other via {@code ||u-v||^2 = 2(1 - cos)}:
 *
 * <pre>
 * Golden 1 (same 3-4-5-rotated vectors as EmbeddingLocalTest):
 *   u = (0.6, 0.8, 0.0), v = (0.0, 0.6, 0.8) - both unit length.
 *   diff = (0.6, 0.2, -0.8); sumSquaredDiff = 0.36 + 0.04 + 0.64 = 1.04
 *   distance = sqrt(1.04) = 1.019803902718557
 *   normalizedScore = clamp(1 - 1.019803902718557/sqrt(2), 0, 1) = 0.2788897449072022
 *   cross-check: 2*(1 - 0.48) = 1.04 == sumSquaredDiff. OK.
 *
 * Golden 2 (opposite unit vectors, d > sqrt(2)):
 *   u = (1.0, 0.0, 0.0), v = (-1.0, 0.0, 0.0)
 *   sumSquaredDiff = 4.0; distance = 2.0 (rawValue keeps this unclamped)
 *   normalizedScore = clamp(1 - 2.0/sqrt(2), 0, 1) = clamp(-0.4142135623730949, 0, 1) = 0.0
 * </pre>
 */
class EmbeddingApiTest {

    private static final double TOLERANCE = 1e-9;
    private final EmbeddingApi embeddingApi = new EmbeddingApi();

    @Test
    void goldenComputesTheHandComputedDistanceAndMappedScore() {
        SimilarityInput a = inputFor(vector("d01", 0.6, 0.8, 0.0));
        SimilarityInput b = inputFor(vector("d02", 0.0, 0.6, 0.8));

        SimilarityResult result = embeddingApi.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.rawValue()).isCloseTo(1.019803902718557, within(TOLERANCE));
        assertThat(result.normalizedScore()).isCloseTo(0.2788897449072022, within(TOLERANCE));
        assertThat(result.degenerate()).isFalse();
    }

    @Test
    void oppositeVectorsBeyondSqrt2AreClampedToZeroSimilarityButRawValueKeepsTheFullDistance() {
        SimilarityInput a = inputFor(vector("d01", 1.0, 0.0, 0.0));
        SimilarityInput b = inputFor(vector("d02", -1.0, 0.0, 0.0));

        SimilarityResult result = embeddingApi.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.rawValue()).isCloseTo(2.0, within(TOLERANCE));
        assertThat(result.normalizedScore()).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void identicalVectorsScoreOne() {
        EmbeddingVector vector = vector("d01", 0.6, 0.8, 0.0);
        SimilarityInput a = inputFor(vector);
        SimilarityInput b = inputFor(vector);

        SimilarityResult result = embeddingApi.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.rawValue()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void computedNanosIsMeasuredAndNonNegative() {
        SimilarityInput a = inputFor(vector("d01", 0.6, 0.8, 0.0));
        SimilarityInput b = inputFor(vector("d02", 0.0, 0.6, 0.8));

        SimilarityResult result = embeddingApi.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.computedNanos()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void computeRejectsAnInputWithNoEmbeddingVector() {
        SimilarityInput a = new SimilarityInput("abstract a", List.of());
        SimilarityInput b = inputFor(vector("d02", 0.0, 0.6, 0.8));

        assertThatNullPointerException().isThrownBy(() -> embeddingApi.compute(a, b, SimilarityContext.EMPTY));
    }

    @Test
    void traceReportsTheSumSquaredDiffDistanceMappingAndProvenance() {
        EmbeddingVector vectorA = vector("d01", 0.6, 0.8, 0.0);
        EmbeddingVector vectorB = vector("d02", 0.0, 0.6, 0.8);
        SimilarityInput a = inputFor(vectorA);
        SimilarityInput b = inputFor(vectorB);

        EmbeddingApiTrace trace = (EmbeddingApiTrace) embeddingApi.trace(a, b, SimilarityContext.EMPTY).orElseThrow();

        assertThat(trace.algorithmId()).isEqualTo("embedding-api");
        assertThat(trace.provider()).isEqualTo("api");
        assertThat(trace.model()).isEqualTo("gemini-embedding-2-preview");
        assertThat(trace.dimension()).isEqualTo(3);
        assertThat(trace.vectorA()).containsExactly(0.6, 0.8, 0.0);
        assertThat(trace.vectorB()).containsExactly(0.0, 0.6, 0.8);
        assertThat(trace.vectorAExcerpt()).containsExactly(0.6, 0.8, 0.0);
        assertThat(trace.vectorBExcerpt()).containsExactly(0.0, 0.6, 0.8);
        assertThat(trace.preNormL2A()).isCloseTo(vectorA.preNormL2(), within(TOLERANCE));
        assertThat(trace.preNormL2B()).isCloseTo(vectorB.preNormL2(), within(TOLERANCE));
        assertThat(trace.sumSquaredDiff()).isCloseTo(1.04, within(TOLERANCE));
        assertThat(trace.distance()).isCloseTo(1.019803902718557, within(TOLERANCE));
        assertThat(trace.normalizedScore()).isCloseTo(0.2788897449072022, within(TOLERANCE));
        assertThat(trace.providerStatus()).isNotBlank();
    }

    @Test
    void traceExcerptIsCappedAtEightDimensionsForAHigherDimensionalVector() {
        double[] raw = new double[10];
        raw[0] = 1.0; // unit vector: a single 1.0 component.
        EmbeddingVector vectorA = new EmbeddingVector("d01", "api", "gemini-embedding-2-preview", 1.0, boxed(raw));
        EmbeddingVector vectorB = new EmbeddingVector("d02", "api", "gemini-embedding-2-preview", 1.0, boxed(raw));

        EmbeddingApiTrace trace = (EmbeddingApiTrace) embeddingApi
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
        return new EmbeddingVector(documentId, "api", "gemini-embedding-2-preview", preNormL2, values);
    }

    private static SimilarityInput inputFor(EmbeddingVector vector) {
        return new SimilarityInput("raw abstract for " + vector.documentId(), List.of(), vector);
    }

    @Test
    void sumSquaredDifferencesRejectsMismatchedDimensions() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmbeddingApi.sumSquaredDifferences(List.of(1.0, 2.0), List.of(1.0)));
    }

    @Test
    void euclideanDistanceFailsClosedOnANegativeSumSquaredDiffInsteadOfSilentlyReturningNaN() {
        // Math.sqrt(-1) is NaN: a bare sqrt call does not reject a corrupted
        // sum-of-squared-differences, it just propagates it as a NaN distance to callers.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmbeddingApi.euclideanDistance(-1.0))
                .withMessageContaining("finite");
    }

    @Test
    void euclideanDistanceFailsClosedOnANonFiniteSumSquaredDiff() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmbeddingApi.euclideanDistance(Double.NaN))
                .withMessageContaining("finite");
    }

    @Test
    void clamp01FailsClosedOnANonFiniteDistanceInsteadOfSilentlyReturningNaN() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmbeddingApi.clamp01(Double.NaN))
                .withMessageContaining("finite");
    }
}
