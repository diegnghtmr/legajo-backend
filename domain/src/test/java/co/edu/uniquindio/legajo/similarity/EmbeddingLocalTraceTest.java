package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Validated invariants of {@link EmbeddingLocalTrace} (TRD §6.3's embedding trace row):
 * {@code vectorA}/{@code vectorB} must match {@code dimension}; the excerpts must be exactly
 * the first {@code min(8, dimension)} components of the corresponding full vector;
 * {@code dotProduct} must equal the hand-computed dot product of the two vectors;
 * {@code cosine} must equal {@code dotProduct} (unit vectors); {@code angleDegrees} must
 * equal {@code degrees(acos(clamp(cosine, -1, 1)))}; {@code normalizedScore} must equal
 * {@code clamp(cosine, 0, 1)}.
 */
class EmbeddingLocalTraceTest {

    private static final List<Double> VECTOR_A = List.of(0.6, 0.8, 0.0);
    private static final List<Double> VECTOR_B = List.of(0.0, 0.6, 0.8);

    private static EmbeddingLocalTrace validTrace() {
        return new EmbeddingLocalTrace("embedding-local", "local", "all-MiniLM-L6-v2", 3, VECTOR_A, VECTOR_B,
                VECTOR_A, VECTOR_B, 1.0, 1.0, 0.48, 0.48, 61.31459798588108, 0.48);
    }

    @Test
    void acceptsAConsistentTrace() {
        validTrace();
    }

    @Test
    void rejectsANullAlgorithmId() {
        assertThatNullPointerException().isThrownBy(() -> new EmbeddingLocalTrace(null, "local", "all-MiniLM-L6-v2",
                3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, 0.48, 0.48, 61.31459798588108, 0.48));
    }

    @Test
    void rejectsAVectorASizeMismatchWithDimension() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingLocalTrace("embedding-local", "local",
                "all-MiniLM-L6-v2", 4, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, 0.48, 0.48,
                61.31459798588108, 0.48));
    }

    @Test
    void rejectsAnExcerptThatIsNotAPrefixOfTheFullVector() {
        List<Double> wrongExcerpt = List.of(9.0, 9.0, 9.0);
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingLocalTrace("embedding-local", "local",
                "all-MiniLM-L6-v2", 3, wrongExcerpt, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, 0.48, 0.48,
                61.31459798588108, 0.48));
    }

    @Test
    void rejectsADotProductThatDoesNotMatchTheVectors() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingLocalTrace("embedding-local", "local",
                "all-MiniLM-L6-v2", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, 0.99, 0.99,
                61.31459798588108, 0.48));
    }

    @Test
    void rejectsACosineThatDiffersFromDotProduct() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingLocalTrace("embedding-local", "local",
                "all-MiniLM-L6-v2", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, 0.48, 0.99,
                61.31459798588108, 0.48));
    }

    @Test
    void rejectsAnAngleThatDoesNotMatchTheCosine() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingLocalTrace("embedding-local", "local",
                "all-MiniLM-L6-v2", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, 0.48, 0.48, 0.0, 0.48));
    }

    @Test
    void rejectsANormalizedScoreThatIsNotTheClampedCosine() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingLocalTrace("embedding-local", "local",
                "all-MiniLM-L6-v2", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, 1.0, 1.0, 0.48, 0.48,
                61.31459798588108, 0.99));
    }

    @Test
    void rejectsANegativePreNormL2() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmbeddingLocalTrace("embedding-local", "local",
                "all-MiniLM-L6-v2", 3, VECTOR_A, VECTOR_B, VECTOR_A, VECTOR_B, -1.0, 1.0, 0.48, 0.48,
                61.31459798588108, 0.48));
    }

    @Test
    void anEgativeCosineNormalizesTheClampedScoreToZero() {
        List<Double> opposite = List.of(-0.6, -0.8, 0.0);
        new EmbeddingLocalTrace("embedding-local", "local", "all-MiniLM-L6-v2", 3, VECTOR_A, opposite, VECTOR_A,
                opposite, 1.0, 1.0, -1.0, -1.0, 180.0, 0.0);
    }
}
