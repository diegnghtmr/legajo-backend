package co.edu.uniquindio.legajo.similarity;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * jqwik properties for {@code embedding-local} (TRD §6.3): symmetry (cosine is commutative),
 * identity (a vector compared with itself scores 1.0 within 1e-9), and the normalized score
 * staying within the closed [0,1] range regardless of how negative the raw cosine is.
 */
class EmbeddingLocalPropertyTest {

    private static final double TOLERANCE = 1e-9;
    private final EmbeddingLocal embeddingLocal = new EmbeddingLocal();

    @Property
    void computeIsSymmetric(@ForAll("unitVectorPair") UnitVectorPair pair) {
        SimilarityResult ab = embeddingLocal.compute(input("a", pair.u()), input("b", pair.v()), SimilarityContext.EMPTY);
        SimilarityResult ba = embeddingLocal.compute(input("b", pair.v()), input("a", pair.u()), SimilarityContext.EMPTY);

        assertThat(ab.normalizedScore()).isCloseTo(ba.normalizedScore(), within(TOLERANCE));
        assertThat(ab.rawValue()).isCloseTo(ba.rawValue(), within(TOLERANCE));
    }

    @Property
    void identicalVectorScoresOne(@ForAll("unitVector") List<Double> values) {
        EmbeddingVector vector = new EmbeddingVector("d", "local", "model", EmbeddingVector.l2Norm(values), values);
        SimilarityResult result = embeddingLocal.compute(input("a", vector), input("b", vector), SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Property
    void normalizedScoreStaysInZeroToOneRange(@ForAll("unitVectorPair") UnitVectorPair pair) {
        SimilarityResult result = embeddingLocal.compute(input("a", pair.u()), input("b", pair.v()), SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isBetween(0.0 - TOLERANCE, 1.0 + TOLERANCE);
    }

    @Provide
    Arbitrary<List<Double>> unitVector() {
        return Arbitraries.doubles().between(-1.0, 1.0).list().ofSize(4)
                .filter(values -> EmbeddingVector.l2Norm(values) > 1e-6)
                .map(EmbeddingLocalPropertyTest::normalized);
    }

    @Provide
    Arbitrary<UnitVectorPair> unitVectorPair() {
        return Combinators.combine(unitVector(), unitVector())
                .as((u, v) -> new UnitVectorPair(
                        new EmbeddingVector("u", "local", "model", EmbeddingVector.l2Norm(u), u),
                        new EmbeddingVector("v", "local", "model", EmbeddingVector.l2Norm(v), v)));
    }

    private static List<Double> normalized(List<Double> raw) {
        double norm = EmbeddingVector.l2Norm(raw);
        return raw.stream().map(value -> value / norm).toList();
    }

    private static SimilarityInput input(String documentId, EmbeddingVector vector) {
        return new SimilarityInput("raw abstract " + documentId, List.of(), vector);
    }

    private record UnitVectorPair(EmbeddingVector u, EmbeddingVector v) {
    }
}
