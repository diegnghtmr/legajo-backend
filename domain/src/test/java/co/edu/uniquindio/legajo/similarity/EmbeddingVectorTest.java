package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link EmbeddingVector} carries one document's cached embedding (TRD §6.3, "Invariante de
 * norma unitaria (fijado)"): {@code values} must always be L2-normalized to unit length
 * (validated structurally, within 1e-9), while {@code preNormL2} is provenance from
 * precompute time — the norm of the raw pooled vector <em>before</em> that normalization —
 * and is not itself constrained to be close to 1.
 */
class EmbeddingVectorTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void l2NormIsHandComputedOverPlainComponents() {
        // 3-4-5 triangle: sqrt(3^2 + 4^2) = 5.
        assertThat(EmbeddingVector.l2Norm(List.of(3.0, 4.0))).isCloseTo(5.0, within(TOLERANCE));
    }

    @Test
    void normalizeDividesEachComponentByTheRawNormAndRecordsItAsPreNormL2() {
        EmbeddingVector vector = EmbeddingVector.normalize("d01", "local", "all-MiniLM-L6-v2", List.of(3.0, 4.0));

        assertThat(vector.preNormL2()).isCloseTo(5.0, within(TOLERANCE));
        assertThat(vector.values()).containsExactly(0.6, 0.8);
        assertThat(EmbeddingVector.l2Norm(vector.values())).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void normalizeRejectsAnAllZeroVector() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EmbeddingVector.normalize("d01", "local", "model", List.of(0.0, 0.0)))
                .withMessageContaining("d01");
    }

    @Test
    void constructorAcceptsAnAlreadyUnitVectorRegardlessOfPreNormL2() {
        // preNormL2 here is provenance from a much larger raw vector; unrelated to 1.
        EmbeddingVector vector = new EmbeddingVector("d01", "local", "model", 5.814322, List.of(0.6, 0.8));

        assertThat(vector.preNormL2()).isCloseTo(5.814322, within(TOLERANCE));
        assertThat(vector.dimension()).isEqualTo(2);
    }

    @Test
    void constructorRejectsAVectorThatIsNotUnitLength() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmbeddingVector("d01", "local", "model", 5.0, List.of(3.0, 4.0)))
                .withMessageContaining("unit");
    }

    @Test
    void constructorRejectsAVectorWithANonFiniteNorm() {
        // A cache corrupted to all-zero components renormalizes to 0.0/0.0 = NaN per
        // component; l2Norm of an all-NaN vector is itself NaN, and the old guard
        // (Math.abs(NaN - 1.0) > 1e-9) is false for NaN, so it used to let this through.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmbeddingVector("d01", "local", "model", 1.0,
                        List.of(Double.NaN, Double.NaN)))
                .withMessageContaining("unit");
    }

    @Test
    void constructorRejectsANegativePreNormL2() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmbeddingVector("d01", "local", "model", -1.0, List.of(0.6, 0.8)));
    }

    @Test
    void constructorRejectsAnEmptyValuesList() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmbeddingVector("d01", "local", "model", 0.0, List.of()));
    }

    @Test
    void constructorRejectsANullDocumentId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new EmbeddingVector(null, "local", "model", 1.0, List.of(1.0)));
    }

    @Test
    void defensivelyCopiesValues() {
        java.util.ArrayList<Double> mutable = new java.util.ArrayList<>(List.of(0.6, 0.8));
        EmbeddingVector vector = new EmbeddingVector("d01", "local", "model", 5.0, mutable);

        mutable.add(0.0);

        assertThat(vector.values()).containsExactly(0.6, 0.8);
    }
}
