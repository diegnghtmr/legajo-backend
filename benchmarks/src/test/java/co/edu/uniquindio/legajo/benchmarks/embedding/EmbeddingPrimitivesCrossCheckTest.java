package co.edu.uniquindio.legajo.benchmarks.embedding;

import co.edu.uniquindio.legajo.similarity.EmbeddingApi;
import co.edu.uniquindio.legajo.similarity.EmbeddingLocal;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import co.edu.uniquindio.legajo.similarity.SimilarityContext;
import co.edu.uniquindio.legajo.similarity.SimilarityInput;
import co.edu.uniquindio.legajo.similarity.SimilarityResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Proves {@link EmbeddingPrimitives} — the loops {@code EmbeddingPrimitiveBenchmark} measures
 * — compute the exact same values the domain's {@link EmbeddingLocal} and {@link EmbeddingApi}
 * algorithms rely on, on fixed unit vectors, via each algorithm's own public {@code
 * compute(...)}. A benchmark that quietly drifted from the domain math it claims to measure
 * would report numbers about the wrong computation; this is the regression guard against that.
 */
class EmbeddingPrimitivesCrossCheckTest {

    // Two fixed unit vectors (0.6^2 + 0.8^2 = 1.0 each), chosen so both the dot product and the
    // sum of squared differences are non-trivial and easy to hand-verify.
    private static final List<Double> U = List.of(0.6, 0.8, 0.0, 0.0);
    private static final List<Double> V = List.of(0.0, 0.6, 0.8, 0.0);

    @Test
    void dotProductMatchesEmbeddingLocalsComputedRawValue() {
        double primitiveDotProduct = EmbeddingPrimitives.dotProduct(U, V);

        SimilarityResult result = new EmbeddingLocal().compute(inputFor("doc-a", U), inputFor("doc-b", V),
                SimilarityContext.EMPTY);

        // EmbeddingLocal.compute's rawValue is the dot product itself: over unit vectors it is
        // already the cosine, so no further transform separates the two values being compared.
        // A small tolerance guards against floating-point summation-order drift between this
        // loop and EmbeddingLocal's own, exactly like the sum-of-squared-differences check below.
        assertThat(primitiveDotProduct).isEqualTo(result.rawValue(), within(1e-12));
    }

    @Test
    void sumOfSquaredDifferencesMatchesEmbeddingApisComputedRawValue() {
        double primitiveSumOfSquaredDifferences = EmbeddingPrimitives.sumOfSquaredDifferences(U, V);

        SimilarityResult result = new EmbeddingApi().compute(inputFor("doc-a", U), inputFor("doc-b", V),
                SimilarityContext.EMPTY);

        // EmbeddingApi.compute's rawValue is the Euclidean distance d = sqrt(sumSquaredDiff),
        // not the sum itself, so squaring it back is what makes the two values comparable.
        double distanceSquared = result.rawValue() * result.rawValue();
        assertThat(primitiveSumOfSquaredDifferences).isEqualTo(distanceSquared, within(1e-12));
    }

    private static SimilarityInput inputFor(String documentId, List<Double> unitVector) {
        EmbeddingVector embeddingVector = new EmbeddingVector(documentId, "local", "test-model", 1.0, unitVector);
        return new SimilarityInput("raw abstract for " + documentId, List.of(), embeddingVector);
    }
}
