package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Structural invariants of {@link JaccardTrace} (TRD §6.3, PRD HU-1.6): the four listed sets
 * must each be sorted ascending with no duplicates (the fixed deterministic order for this
 * trace, documented on the type itself), the size fields must match their listing's actual
 * size, {@code intersection} must equal {@code setA ∩ setB}, {@code union} must equal
 * {@code setA ∪ setB}, and {@code coefficient} must match {@code intersectionSize /
 * unionSize} (or {@code 1.0} when {@code unionSize} is 0).
 */
class JaccardTraceTest {

    @Test
    void acceptsAConsistentTrace() {
        assertThatNoException().isThrownBy(() -> new JaccardTrace(
                "jaccard", List.of("a", "b", "c"), List.of("a", "c", "x"),
                2, 4, List.of("a", "c"), List.of("a", "b", "c", "x"), 0.5));
    }

    @Test
    void acceptsTheBothEmptyConvention() {
        assertThatNoException().isThrownBy(() -> new JaccardTrace(
                "jaccard", List.of(), List.of(), 0, 0, List.of(), List.of(), 1.0));
    }

    @Test
    void rejectsAnUnsortedSetA() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JaccardTrace(
                        "jaccard", List.of("c", "a"), List.of("a"),
                        1, 2, List.of("a"), List.of("a", "c"), 0.5))
                .withMessageContaining("setA");
    }

    @Test
    void rejectsADuplicateInUnion() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JaccardTrace(
                        "jaccard", List.of("a"), List.of("a"),
                        1, 1, List.of("a"), List.of("a", "a"), 1.0))
                .withMessageContaining("union");
    }

    @Test
    void rejectsAnIntersectionSizeMismatch() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JaccardTrace(
                        "jaccard", List.of("a", "b"), List.of("a"),
                        2, 2, List.of("a"), List.of("a", "b"), 0.5))
                .withMessageContaining("intersectionSize");
    }

    @Test
    void rejectsAUnionSizeMismatch() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JaccardTrace(
                        "jaccard", List.of("a", "b"), List.of("a"),
                        1, 3, List.of("a"), List.of("a", "b"), 0.5))
                .withMessageContaining("unionSize");
    }

    @Test
    void rejectsAnIntersectionThatDoesNotMatchSetAAndSetB() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JaccardTrace(
                        "jaccard", List.of("a", "b"), List.of("a"),
                        1, 2, List.of("b"), List.of("a", "b"), 0.5))
                .withMessageContaining("intersection");
    }

    @Test
    void rejectsAUnionThatDoesNotMatchSetAAndSetB() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JaccardTrace(
                        "jaccard", List.of("a", "b"), List.of("a"),
                        1, 2, List.of("a"), List.of("a", "c"), 0.5))
                .withMessageContaining("union");
    }

    @Test
    void rejectsACoefficientThatDoesNotMatchIntersectionOverUnion() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JaccardTrace(
                        "jaccard", List.of("a", "b"), List.of("a"),
                        1, 2, List.of("a"), List.of("a", "b"), 0.9))
                .withMessageContaining("coefficient");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsANonFiniteCoefficient(double nonFinite) {
        // Math.abs(NaN - expected) > tolerance is false, so the old guard let a NaN
        // coefficient through silently instead of failing closed.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JaccardTrace(
                        "jaccard", List.of("a", "b"), List.of("a"),
                        1, 2, List.of("a"), List.of("a", "b"), nonFinite))
                .withMessageContaining("coefficient");
    }
}
